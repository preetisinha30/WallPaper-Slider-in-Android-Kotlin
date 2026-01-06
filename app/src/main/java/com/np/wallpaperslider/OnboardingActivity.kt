package com.np.wallpaperslider

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.np.wallpaperapp.MainViewModel
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class OnboardingActivity:AppCompatActivity() {

    private val PREFS_NAME = "wallpaperimages"
    private val PREF_ONBOARDING_COMPLETED = "onboarding_completed"
    private val viewModel: MainViewModel by viewModels()
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayoutIndicator: TabLayout
    private lateinit var nextButton : Button
    private lateinit var skipButton : Button

    private var isScrolling = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().apply {
            setKeepOnScreenCondition{viewModel.isLoading.value}
        }
        super.onCreate(savedInstanceState)
        AppLogger.i("onboarding", "in onboardingcreate")
        if (isOnboardingCompleted()) {
            startMainActivity()
            return
        }

        setContentView(R.layout.activity_onboarding)
        viewPager = findViewById(R.id.viewPager)
        nextButton = findViewById<Button>(R.id.btn_next)
        skipButton = findViewById<Button>(R.id.btn_skip)
        tabLayoutIndicator = findViewById(R.id.tab_layout_indicator)
        val adapter = OnboardingPagerAdapter(this)
        viewPager.adapter = adapter
        tabLayoutIndicator.post {
            try {
                val tabStrip = tabLayoutIndicator.getChildAt(0) as ViewGroup
                val desiredHeightPx = resources.getDimensionPixelSize(R.dimen.tab_dot_height_enforced) // Use a dimension resource

                for (i in 0 until tabStrip.childCount) {
                    val tabView = tabStrip.getChildAt(i)
                    // Enforce small height to prevent stretching into an oval
                    tabView.layoutParams.height = desiredHeightPx

                    // Set margins for spacing between dots (6dp is 6 * 1.5 ~ 9px density-adjusted)
                    val p = tabView.layoutParams as ViewGroup.MarginLayoutParams
                    p.setMargins(6, 0, 6, 0)

                    tabView.requestLayout()
                }
            } catch (e: Exception) {
                AppLogger.e("OnboardingActivity", "Error setting tab dimensions: ${e.message}")
            }
        }
        TabLayoutMediator(tabLayoutIndicator, viewPager) { tab, position ->
            // No need to set text, just attach for dots
        }.attach()
        /*for (i in 0 until tabLayoutIndicator.tabCount) {
            val tab = (tabLayoutIndicator.getChildAt(0) as ViewGroup).getChildAt(i)
            val p = tab.layoutParams as ViewGroup.MarginLayoutParams
            p.setMargins(6, 0, 6, 0) // adds small space between dots
            //tab.setPadding(6, 0, 6, 0)
            tab.minimumWidth = 0
            tab.requestLayout()

        }

        tabLayoutIndicator.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                tab.view.animate().scaleX(1.4f).scaleY(1.4f).setDuration(150).start()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                tab.view.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }

            override fun onTabReselected(tab: TabLayout.Tab) {}
        })*/

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                val totalPages = viewPager.adapter?.itemCount ?: 0

                // Handle visibility here for a smoother feel
                if (position > 0) {
                    skipButton.visibility = View.VISIBLE
                } else {
                    // Usually hidden on first page
                    skipButton.visibility = View.VISIBLE
                }

                if (position == totalPages - 1) {
                    nextButton.text = "Finish" // Changing text is better than hiding
                } else {
                    nextButton.text = "Next"
                }
            }
            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                // SCROLL_STATE_IDLE means the animation is finished
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    isScrolling = false // Unlock the button
                }
            }
        })
        // **Handle "Next" click**
        nextButton.setOnClickListener {
            this.onNextClicked()
        }

        // **Handle "Skip" click**
        skipButton.setOnClickListener {
            this.onSkipClicked()
        }
    }

    fun onNextClicked() {
       /* val currentItem = viewPager.currentItem
        val totalPages = viewPager.adapter?.itemCount ?: 0

        if (currentItem < totalPages - 1) {
            // **Increment the counter (Go to next page)**
            skipButton.visibility = View.VISIBLE
            viewPager.setCurrentItem(currentItem + 1, true)
        } else {
            // Last page reached, complete onboarding
            skipButton.visibility = View.GONE
            startMainActivity()
        }*/
        if (isScrolling) return

        val currentItem = viewPager.currentItem
        val totalPages = viewPager.adapter?.itemCount ?: 0

        if (currentItem < totalPages - 1) {
            isScrolling = true // Lock the button
            viewPager.setCurrentItem(currentItem + 1, true)
        } else {
            startMainActivity()
        }
    }

    fun onSkipClicked() {
        // **Skip button takes to MainActivity**
        startMainActivity()
    }

    private fun isOnboardingCompleted(): Boolean {
        AppLogger.i("onboarding", "in isOnboardingCompleted")
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_ONBOARDING_COMPLETED, false)
    }

    private fun setOnboardingCompleted(isCompleted: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        with (prefs.edit()) {
            putBoolean(PREF_ONBOARDING_COMPLETED, isCompleted)
            apply()
        }
    }

    // Navigates to MainActivity and sets the flag
    private fun startMainActivity() {
        // 2. **Set the flag to true**
        setOnboardingCompleted(true)
        val prefs = getSharedPreferences("wallpaperimages", Context.MODE_PRIVATE)
        val size = prefs.getInt("imagesPathList_size", 0)
        if(iswallpaperSet() || size>0)
        {
            val intent = Intent(this, RGrid::class.java)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.putExtra("frompage","main")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
        }
        else
        {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
        finish() // Prevents returning to the onboarding screen
    }

    private fun iswallpaperSet(): Boolean {
        try {
            val wpm = WallpaperManager.getInstance(this)
            val info = wpm.wallpaperInfo
            if (info != null && info.packageName == this.packageName) {
                AppLogger.d("bitmappos", "We're already running")
                return true
            } else {
                AppLogger.d("bitmappos", "We're not running")
                return false
            }
        } catch (e: Exception) {
            AppLogger.e("bitmappos....", e.message.toString(), e)
        }
        return false
    }
}