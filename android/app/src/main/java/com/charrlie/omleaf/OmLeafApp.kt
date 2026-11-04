package com.charrlie.omleaf

import android.app.Application
import com.charrlie.omleaf.data.Repository

class OmLeafApp : Application() {
    val repository by lazy { Repository(this) }
}
