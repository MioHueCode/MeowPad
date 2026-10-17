package com.miohue.notepad

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.miohue.notepad.ui.NoteApp

class MainActivity : ComponentActivity() {

    /** 按用户选择的应用内语言包装 base context（低版本方案；33+ 走 LocaleManager 同步）。 */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NoteApp(context = this)
        }
    }
}
