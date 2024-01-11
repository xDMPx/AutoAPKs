package com.xdmpx.autoapks

import android.content.Context
import com.android.volley.RequestQueue
import com.android.volley.toolbox.Volley

abstract class VRequestQueue {

    companion object {
        @Volatile
        private var INSTANCE: RequestQueue? = null

        fun getInstance(context: Context): RequestQueue {
            synchronized(this) {
                return INSTANCE ?: Volley.newRequestQueue(
                    context
                ).also {
                    INSTANCE = it
                }
            }
        }
    }
}
