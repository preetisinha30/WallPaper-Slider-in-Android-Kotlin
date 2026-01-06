package com.np.wallpaperslider

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken


class OnboardingFragment: Fragment() {




    companion object {
        private const val ARG_IMAGE_RES = "image_res"
        private const val ARG_TITLE = "title"
        private const val ARG_DESC = "description"

        fun newInstance(item: OnboardingItem): OnboardingFragment {
            return OnboardingFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_IMAGE_RES, item.imageResId)
                    putString(ARG_TITLE, item.title)
                    //putString(ARG_DESC, item.description)

                }
            }
        }
    }

        override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            val view = inflater.inflate(R.layout.fragment_onboarding, container, false)

            // Retrieve arguments
            val imageRes = arguments?.getInt(ARG_IMAGE_RES) ?: 0
            val title = arguments?.getString(ARG_TITLE)
            val description = arguments?.getString(ARG_DESC)

            // Find views (assuming fragment_onboarding.xml layout exists with these IDs)
            val imageView = view.findViewById<ImageView>(R.id.image_onboarding)
            val titleView = view.findViewById<TextView>(R.id.text_title)
            //val descView = view.findViewById<TextView>(R.id.text_description)


            // Set data
            imageView.setImageResource(imageRes)
            titleView.text = title
            //descView.text = description

            //descView.alpha = 0f
           // descView.translationY = 150f // 50 pixels down


            /*descView.animate()
                .alpha(1f)              // Fade in to full opacity
                .translationY(0f)       // Slide up to its original position
                .setDuration(600)       // Duration of the animation (e.g., 600ms)
                .setStartDelay(200)     // Optional: slight delay after the page loads
                .start()
            */





            return view
        }




}
