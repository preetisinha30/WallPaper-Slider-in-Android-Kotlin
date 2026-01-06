package com.np.wallpaperslider

import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class OnboardingPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity)  {
    override fun getItemCount(): Int = onboardingPages.size

    override fun createFragment(position: Int): Fragment {
        val item = onboardingPages[position]
        return OnboardingFragment.newInstance(item)
    }
}
