package com.example.kubernetesassistant;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatButton;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.example.kubernetesassistant.AppConfig.API_KEY;
import static com.example.kubernetesassistant.AppConfig.SERVER_URL;
import static com.example.kubernetesassistant.MainApplication.LOG_TAG;

public class MainActivity extends AppCompatActivity {

    private static final String SHARED_PREFERENCES_NAME = "AuthStatePreference";
    private static final String AUTH_STATE = "AUTH_STATE";
    private static final String USED_INTENT = "USED_INTENT";
    private static final String IBM_CLOUD_ACCOUNTS_URL = "https://accountmanagement.bluemix.net/coe/v2/accounts";
    private static final String IBM_CLOUD_RESOURCE_GROUPS_URL = "https://resource-controller.cloud.ibm.com/v1/resource_groups";
    private static final String IBM_CLOUD_IAM_TOKEN_URL = "https://iam.cloud.ibm.com/identity/token";
    private static final String IBM_CLOUD_IAM_AUTH_URL = "https://iam.cloud.ibm.com/identity/authorize";

    private final int REQ_CODE_SPEECH_INPUT = 100;

    private TextToSpeech textToSpeech;

    MainApplication mMainApplication;

    // state
    JSONObject mAuthState;

    RequestQueue httpQueue;

    // views
    AppCompatButton mAuthorize;
    ImageButton mBtnSpeak;
    AppCompatButton mSignOut;
    LinearLayout mVoiceView;
    TextView mTxtSpeechInput;
    RadioGroup mRadioGroup;
    RadioGroup mRadioGroupForResources;
    ListView mClusterListView;

    JSONObject mIbmAccountInfo;
    JSONObject mIbmResourceGroups;
    String mAssistantSession;
    String mResourceGroup;

    List<String> mClusterList;
    JSONArray mClusterListVerbose;
    ArrayAdapter<String> mClusterListAdapter;

    String mAccountId = "";
    String mConversationContext = "";
    String mPrevInput = "";
    String mRegion = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_talk);
        mMainApplication = (MainApplication) getApplication();
        mAuthorize = findViewById(R.id.authorize);
        mBtnSpeak = findViewById(R.id.btnSpeak);
        mSignOut = findViewById(R.id.signOut);
        mVoiceView = findViewById(R.id.voiceLayout);
        mTxtSpeechInput = findViewById(R.id.txtSpeechInput);
        mRadioGroup = findViewById(R.id.ibmAccounts);
        mRadioGroupForResources = findViewById(R.id.ibmResourceGroups);
        mClusterListView = findViewById(R.id.clusterList);

        mClusterListVerbose = new JSONArray();
        mClusterList = new ArrayList<>();
        mClusterListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, mClusterList);
        mClusterListView.setAdapter(mClusterListAdapter);
        mClusterListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Log.i(LOG_TAG, "cluster list position: " + position);

                Intent intent = new Intent();
                intent.setClass(getApplicationContext(), ClusterVerboseActivity.class);
                try {
                    intent.putExtra("cluster", mClusterListVerbose.getJSONObject(position).toString());
                } catch (JSONException e) {
                    Log.e(LOG_TAG, e.toString());
                    return;
                }
                startActivity(intent);
            }
        });

        httpQueue = Volley.newRequestQueue(this);

        enablePostAuthorizationFlows();
        toggleUI();

        // wire click listeners
        mAuthorize.setOnClickListener(new AuthorizeListener());
        mSignOut.setOnClickListener(new SignOutListener(this));
        mRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                try {
                    View rbn = mRadioGroup.findViewById(checkedId);
                    int index = mRadioGroup.indexOfChild(rbn);
                    mRadioGroup.setVisibility(View.GONE);

                    mAccountId = mIbmAccountInfo.getJSONArray("resources").getJSONObject(index).getJSONObject("entity").getString("customer_id");
                    getIAMToken(mAccountId);
                } catch (JSONException e) {
                    Log.e(LOG_TAG, e.toString());
                }
            }
        });
        mRadioGroupForResources.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                try {
                    View rbn = mRadioGroupForResources.findViewById(checkedId);
                    int index = mRadioGroupForResources.indexOfChild(rbn);
                    mRadioGroupForResources.setVisibility(View.GONE);

                    mResourceGroup = mIbmResourceGroups.getJSONArray("resources").getJSONObject(index).getString("id");
                    Log.i(LOG_TAG, "Resource Group ID: " + mResourceGroup);

                    mSignOut.setVisibility(View.VISIBLE);
                    getAssistantSession();

                    Toast.makeText(getApplicationContext(), "Brewing kubernetes magic...", Toast.LENGTH_SHORT).show();
                } catch (JSONException e) {
                    Log.e(LOG_TAG, e.toString());
                }
            }
        });
        mBtnSpeak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptSpeechInput();
            }
        });
        textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int ttsLang = textToSpeech.setLanguage(Locale.US);

                    if (ttsLang == TextToSpeech.LANG_MISSING_DATA
                            || ttsLang == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTS", "The Language is not supported!");
                    } else {
                        Log.i("TTS", "Language Supported.");
                    }
                    Log.i("TTS", "Initialization success.");
                } else {
                    Toast.makeText(getApplicationContext(), "TTS Initialization failed!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void enablePostAuthorizationFlows() {
        mAuthState = restoreAuthState();
    }

    private void toggleUI() {
        if (mAuthState != null) {
            if (mSignOut.getVisibility() == View.GONE) {
                mSignOut.setVisibility(View.VISIBLE);
            }
            if (mVoiceView.getVisibility() == View.GONE) {
                mVoiceView.setVisibility(View.VISIBLE);
                mTxtSpeechInput.setVisibility(View.VISIBLE);
            }
        } else {
            mVoiceView.setVisibility(View.GONE);
            mTxtSpeechInput.setVisibility(View.GONE);
            mSignOut.setVisibility(View.GONE);
            mClusterListView.setVisibility(View.GONE);
            mAuthorize.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Makes call with API-KEY to IAM.
     */
    private void startAuthorizationProcess() {

        StringRequest request = new StringRequest(

                Request.Method.POST,
                IBM_CLOUD_IAM_TOKEN_URL,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String responseStr) {
                        Log.i(LOG_TAG, responseStr);
                        JSONObject response = null;
                        try {
                            response = new JSONObject(responseStr);
                        } catch (JSONException e) {
                            Log.e(LOG_TAG, e.toString());
                        }

                        persistAuthState(response);

                        try {
                            getAccountsAsccociatedWithLogin(response.getString("access_token"));
                        } catch (JSONException e) {
                            Log.e(LOG_TAG, e.toString());
                        }
                    }
                },
                new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.i(LOG_TAG, "HTTP ERROR: get initial iam token");
                    }
                }) {

            @Override
            public String getBodyContentType() {
                return "application/x-www-form-urlencoded; charset=UTF-8";
            }

            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> params = new HashMap<>();
                params.put("grant_type", "urn:ibm:params:oauth:grant-type:apikey");
                params.put("apikey", API_KEY);
                return params;
            }
        };

        httpQueue.add(request);
    }

    private void getAccountsAsccociatedWithLogin(final String accessToken) {
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, IBM_CLOUD_ACCOUNTS_URL, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.i(LOG_TAG, response.toString());
                        mIbmAccountInfo = response;
                        try {
                            if (response.getInt("total_results") > 0) {
                                mRadioGroup.removeAllViews();

                                for (int i = 0; i < response.getInt("total_results"); i++) {
                                    RadioButton rbn = new RadioButton(getApplicationContext());
                                    rbn.setId(View.generateViewId());
                                    rbn.setTextSize(26);
                                    rbn.setTextColor(Color.WHITE);
                                    rbn.setButtonTintList(ColorStateList.valueOf(Color.WHITE));
                                    rbn.setHighlightColor(Color.WHITE);
                                    if (response.getJSONArray("resources").getJSONObject(i).getJSONObject("entity").getJSONArray("bluemix_subscriptions").getJSONObject(0).getString("type").equalsIgnoreCase("TRIAL"))
                                        rbn.setText(response.getJSONArray("resources").getJSONObject(i).getJSONObject("entity").getJSONArray("bluemix_subscriptions").getJSONObject(0).getString("type"));
                                    else
                                        rbn.setText(response.getJSONArray("resources").getJSONObject(i).getJSONObject("entity").getJSONArray("bluemix_subscriptions").getJSONObject(0).getString("softlayer_account_id"));
                                    mRadioGroup.addView(rbn);
                                }

                                mAuthorize.setVisibility(View.GONE);
                                mRadioGroup.setVisibility(View.VISIBLE);
                                talk("Select an account");
                            } else {
                                Log.e(LOG_TAG, "No cloud accounts associated");
                            }
                        } catch (JSONException e) {
                            Log.e(LOG_TAG, e.toString());
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.i(LOG_TAG, "HTTP ERROR: get IBM accounts");
            }
        }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> params = new HashMap<>();
                params.put("Authorization", "Bearer ".concat(accessToken));
                return params;
            }
        };

        httpQueue.add(request);
    }

    private void getIAMToken(final String accountId) {
        StringRequest request = new StringRequest(

                Request.Method.POST,
                IBM_CLOUD_IAM_TOKEN_URL,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String responseStr) {
                        Log.i(LOG_TAG, responseStr);
                        JSONObject response = null;
                        try {
                            response = new JSONObject(responseStr);
                        } catch (JSONException e) {
                            Log.e(LOG_TAG, e.toString());
                        }

                        persistAuthState(response);

                        try {
                            getResourceGroupsAssociatedWithAccount(response.getString("access_token"));
                        } catch (JSONException e) {
                            Log.e(LOG_TAG, e.toString());
                        }
                    }
                },
                new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.i(LOG_TAG, "HTTP ERROR: get account iam token");
                    }
                }) {

            @Override
            public String getBodyContentType() {
                return "application/x-www-form-urlencoded; charset=UTF-8";
            }

            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> params = new HashMap<>();
                params.put("grant_type", "urn:ibm:params:oauth:grant-type:apikey");
                params.put("apikey", API_KEY);
                params.put("account", accountId);
                return params;
            }
        };

        httpQueue.add(request);
    }

    private void getFreshIAMTokenAndPerformVoiceRequest(final String voiceText) {
        StringRequest request = new StringRequest(

                Request.Method.POST,
                IBM_CLOUD_IAM_TOKEN_URL,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String responseStr) {
                        Log.i(LOG_TAG, responseStr);
                        JSONObject response = null;
                        try {
                            response = new JSONObject(responseStr);
                        } catch (JSONException e) {
                            Log.e(LOG_TAG, e.toString());
                        }

                        persistAuthState(response);

                        try {
                            performVoiceRequest(voiceText, response.getString("access_token"));
                        } catch (JSONException e) {
                            Log.e(LOG_TAG, e.toString());
                        }
                    }
                },
                new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.i(LOG_TAG, "HTTP ERROR: get account iam token");
                    }
                }) {

            @Override
            public String getBodyContentType() {
                return "application/x-www-form-urlencoded; charset=UTF-8";
            }

            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> params = new HashMap<>();
                params.put("grant_type", "urn:ibm:params:oauth:grant-type:apikey");
                params.put("apikey", API_KEY);
                params.put("account", mAccountId);
                return params;
            }
        };

        httpQueue.add(request);
    }

    private void getResourceGroupsAssociatedWithAccount(final String accessToken) {
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, IBM_CLOUD_RESOURCE_GROUPS_URL, null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.i(LOG_TAG, response.toString());
                        mIbmResourceGroups = response;
                        try {
                            if (response.getJSONArray("resources").length() > 0) {
                                mRadioGroupForResources.removeAllViews();

                                for (int i = 0; i < response.getJSONArray("resources").length(); i++) {
                                    RadioButton rbn = new RadioButton(getApplicationContext());
                                    rbn.setId(View.generateViewId());
                                    rbn.setTextSize(26);
                                    rbn.setTextColor(Color.WHITE);
                                    rbn.setButtonTintList(ColorStateList.valueOf(Color.WHITE));
                                    rbn.setHighlightColor(Color.WHITE);
                                    rbn.setText(response.getJSONArray("resources").getJSONObject(i).getString("name"));
                                    mRadioGroupForResources.addView(rbn);
                                }

                                talk("select a resource group");
                                mRadioGroupForResources.setVisibility(View.VISIBLE);
                            } else {
                                Log.e(LOG_TAG, "No resource groups associated");
                            }
                        } catch (JSONException e) {
                            Log.e(LOG_TAG, e.toString());
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.i(LOG_TAG, "HTTP ERROR: get Resource Groups");
            }
        }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> params = new HashMap<>();
                params.put("Authorization", "Bearer ".concat(accessToken));
                return params;
            }
        };

        httpQueue.add(request);
    }

    private void getAssistantSession() {
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, SERVER_URL + "/session", null,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.i(LOG_TAG, response.toString());
                        try {
                            if (response.getString("session") != null) {
                                mAssistantSession = response.getString("session");
                                Log.i(LOG_TAG, "Assistant session: " + mAssistantSession);

                                toggleUI();
                            } else {
                                Log.e(LOG_TAG, "No session found");
                            }
                        } catch (JSONException e) {
                            Log.e(LOG_TAG, e.toString());
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.i(LOG_TAG, "HTTP ERROR: get assistant session" + error);
            }
        });

        httpQueue.add(request);
    }

    private void persistAuthState(@NonNull JSONObject authState) {
        getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE).edit()
                .putString(AUTH_STATE, authState.toString())
                .apply();
        enablePostAuthorizationFlows();
    }

    private void clearAuthState() {
        getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(AUTH_STATE)
                .apply();
    }

    @Nullable
    private JSONObject restoreAuthState() {
        String jsonString = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getString(AUTH_STATE, null);

        if (!TextUtils.isEmpty(jsonString)) {
            try {
                JSONObject json = new JSONObject(jsonString);
                return json;
            } catch (JSONException e) {
                // should never happen
                Log.i("fail", e.toString());
            }
        }
        return null;
    }

    /**
     * Kicks off the authorization flow.
     */
    public class AuthorizeListener implements Button.OnClickListener {
        @Override
        public void onClick(View view) {
            startAuthorizationProcess();
        }
    }

    public class SignOutListener implements Button.OnClickListener {

        private final MainActivity mMainActivity;

        public SignOutListener(@NonNull MainActivity mainActivity) {
            mMainActivity = mainActivity;
        }

        @Override
        public void onClick(View view) {
            mMainActivity.mAuthState = null;
            mMainActivity.clearAuthState();
            mMainActivity.enablePostAuthorizationFlows();
            toggleUI();
        }
    }

    /**
     * Showing google speech input dialog
     */
    private void promptSpeechInput() {
        //performVoiceRequest("create standard cluster in us east with 6 cores and 8 ram");

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
                getString(R.string.speech_prompt));
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(getApplicationContext(),
                    getString(R.string.speech_not_supported),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Receiving speech input
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQ_CODE_SPEECH_INPUT: {
                if (resultCode == RESULT_OK && null != data) {

                    ArrayList<String> result = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

                    mTxtSpeechInput.setText(result.get(0));

                    // Get fresh IAM token and perform action
                    getFreshIAMTokenAndPerformVoiceRequest(result.get(0));
                }
                break;
            }

        }
    }

    private void performVoiceRequest(final String voiceText, final String accessToken) {
        Log.i("fresh_token", accessToken);

        JSONObject jsonBody = new JSONObject();

        try {
            jsonBody.put("authorization", "Bearer ".concat(accessToken));
            jsonBody.put("resourceGroup", mResourceGroup);
            jsonBody.put("input", voiceText);
            jsonBody.put("session", mAssistantSession);
            if (!mConversationContext.isEmpty())
                jsonBody.put("conversationContext", mConversationContext);
            if (!mPrevInput.isEmpty())
                jsonBody.put("prevInput", mPrevInput);
            if (!mRegion.isEmpty())
                jsonBody.put("region", mRegion);
            if (mClusterListVerbose.length() > 0)
                jsonBody.put("clusterList", mClusterListVerbose);
        } catch (JSONException err) {
            Log.e(LOG_TAG, err.toString());
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, SERVER_URL + "/message", jsonBody,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.i(LOG_TAG, response.toString());
                        handleClusterList(response);

                        try {
                            if (response.has("context")) {
                                mConversationContext = response.getString("context");
                            }
                            if (response.has("input")) {
                                mPrevInput = response.getString("input");
                            }
                            if (response.has("region")) {
                                mRegion = response.getString("region");
                            }
                        } catch (JSONException e) {
                            Log.e(LOG_TAG, e.toString());
                        }

                        try {
                            mTxtSpeechInput.setText(response.getString("text"));
                            talk(response.getString("text"));
                        } catch (JSONException e) {
                            Log.e(LOG_TAG, e.toString());
                        }
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.i(LOG_TAG, error.toString());
            }
        });

        request.setRetryPolicy(
                new DefaultRetryPolicy(
                        0,
                        DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                        DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

        httpQueue.add(request);
    }

    private void handleClusterList(JSONObject json) {
        try {
            if (!json.has("data")) {
                mClusterListView.setVisibility(View.GONE);
                mClusterListVerbose = new JSONArray();
                mClusterList.clear();
                mRegion = "";

                return;
            }

            mClusterList.clear();
            mClusterListVerbose = json.getJSONArray("data");

            for (int i = 0; i < mClusterListVerbose.length(); i++) {
                JSONObject cluster = mClusterListVerbose.getJSONObject(i);
                int num = i + 1;
                mClusterList.add(num + ". " + cluster.getString("name"));
            }

            Log.i(LOG_TAG, mClusterList.toString());
            mClusterListView.setVisibility(View.VISIBLE);
            mClusterListAdapter.notifyDataSetChanged();

        } catch (JSONException e) {
            Log.e(LOG_TAG, e.toString());
        }
    }

    private void talk(String text) {
        int speechStatus;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            speechStatus = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        } else {
            speechStatus = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }

        if (speechStatus == TextToSpeech.ERROR) {
            Log.e(LOG_TAG, "Error in converting Text to Speech!");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }
}
