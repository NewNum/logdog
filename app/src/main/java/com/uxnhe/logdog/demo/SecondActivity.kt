package com.uxnhe.logdog.demo

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.uxnhe.logdog.LogDoy

class SecondActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second)
        LogDoy.log("Second", "opened")
        findViewById<Button>(R.id.btn_finish).setOnClickListener { finish() }
    }
}
