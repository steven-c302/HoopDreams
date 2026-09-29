package com.partyos.tv

import android.app.Application

class PartyOsApp : Application() {
    val runtime by lazy { PartyRuntime(this) }
}

val android.content.Context.partyRuntime: PartyRuntime get() = (applicationContext as PartyOsApp).runtime
