package com.dardangjinovci.googlevoicechrome

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v7.app.AppCompatActivity
import android.view.KeyEvent
import android.view.View
import android.widget.AdapterView
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private val myHandler = Handler(Looper.getMainLooper())
    private val rmsHandler = Handler()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        auto.setOnCheckedChangeListener { _, isChecked ->

            myHandler.removeCallbacksAndMessages(null)

            if (isChecked) {
                myHandler.postDelayed(object : Runnable {
                    override fun run() {
                        val sel = (spinner.selectedItemPosition + 1) % spinner.count
                        spinner.setSelection(sel)
                        myHandler.postDelayed(this, 4000)
                    }
                }, 4000)
            }
        }


        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {

                rmsHandler.removeCallbacksAndMessages(null)

                when (position) {
                    0 -> googleDotsView.state = State.IDLE
                    1 -> googleDotsView.state = State.LISTENING
                    2 -> {
                        googleDotsView.state = State.USER_SPEAKS
                        rmsHandler.post(object : Runnable {
                            override fun run() {
                                googleDotsView.rms = ((Math.random() * 200.0).toFloat())
                                rmsHandler.postDelayed(this, 50)
                            }
                        })
                    }
                    3 -> googleDotsView.state = State.THINKING
                    4 -> googleDotsView.state = State.REPLYING
                    5 -> googleDotsView.state = State.INCOMPREHENSION
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            var sel = spinner.selectedItemPosition + 1
            if (sel == spinner.count) sel = 0
            spinner.setSelection(sel)

        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            var sel = spinner.selectedItemPosition - 1
            if (sel == -1) sel = spinner.count - 1
            spinner.setSelection(sel)
        }

        return super.onKeyDown(keyCode, event)
    }

}
