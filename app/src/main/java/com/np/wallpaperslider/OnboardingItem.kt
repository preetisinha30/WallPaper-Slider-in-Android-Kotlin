package com.np.wallpaperslider

data class OnboardingItem (val imageResId: Int,
                           val title: String,
                           val description: String
)


val onboardingPages = listOf(
    OnboardingItem(R.drawable.screen1, "Getting Started", "Tap Add Image to add from Gallery and Tap MyImages for added Images"),
    OnboardingItem(R.drawable.screen3, "Assign Category", "Long press any image to open options menu and set image to Home, Lock or Both screens"),
    OnboardingItem(R.drawable.screen6, "Image Editor", "Crop, Rotate, Zoom selected Image") ,
    OnboardingItem(R.drawable.screen12, "You're Ready!", "Choose Home Screen & Lock Screen")
)

