package com.imi.faceunionpayset.detail;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.widget.TextView;

import com.imi.faceunionpayset.R;

public class AuthActivity extends AppCompatActivity {
    private TextView mTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        mTextView = findViewById(R.id.tv_auth_auth);
        if (!TextUtils.isEmpty(getIntent().getStringExtra("Auth"))) {
            mTextView.setText(getIntent().getStringExtra("Auth"));
        }
    }
}
