package com.example.kubernetesassistant;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ClusterVerboseActivity extends Activity {

    ListView mClusterPropertiesView;
    List<String> mClusterProperties;
    ArrayAdapter<String> mClusterPropertiesAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cluster_verbose);

        mClusterPropertiesView = findViewById(R.id.clusterProperties);

        mClusterProperties = new ArrayList<>();
        mClusterPropertiesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, mClusterProperties);
        mClusterPropertiesView.setAdapter(mClusterPropertiesAdapter);

        Intent intent = getIntent();
        String clusterString = intent.getStringExtra("cluster");
        try {
            JSONObject cluster = new JSONObject(clusterString);

            Iterator<String> iter = cluster.keys();
            while (iter.hasNext()) {
                String key = iter.next();
                String value = "";
                try {
                    value = cluster.get(key).toString();
                } catch (JSONException e) {
                    Log.e("Error", e.toString());
                }
                mClusterProperties.add(key + ": " + value);
            }

            mClusterPropertiesAdapter.notifyDataSetChanged();

        } catch (JSONException e) {
            Log.e("Error", e.toString());
        }
    }
}
