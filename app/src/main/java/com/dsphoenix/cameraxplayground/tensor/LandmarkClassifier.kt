package com.dsphoenix.cameraxplayground.tensor

import android.graphics.Bitmap

interface LandmarkClassifier {

    fun classify(bitmap: Bitmap, rotationDegrees: Int): List<Classification>
}
