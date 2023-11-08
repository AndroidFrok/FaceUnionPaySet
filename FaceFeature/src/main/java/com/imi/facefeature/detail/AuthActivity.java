package com.imi.facefeature.detail;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.imi.facefeature.R;


public class AuthActivity extends AppCompatActivity {
    private TextView mTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        mTextView = findViewById(R.id.tv_auth_auth);

        mTextView.setText(getIntent().getStringExtra("Auth"));
    }
}
