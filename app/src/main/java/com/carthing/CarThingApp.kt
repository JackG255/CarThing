package com.carthing

import android.app.Application
import com.carthing.data.AppContainer

class CarThingApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
