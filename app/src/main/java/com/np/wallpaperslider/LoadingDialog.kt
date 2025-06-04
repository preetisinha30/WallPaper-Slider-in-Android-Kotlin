package com.np.wallpaperslider

import android.app.Activity
import android.app.AlertDialog

class LoadingDialog(val mActivity: Activity) {
    private lateinit var isdialog: AlertDialog
    fun startLoading()
    {
        var inflater = mActivity.layoutInflater
        var dialogView = inflater.inflate(R.layout.loading_item,null)
        var builder = AlertDialog.Builder(mActivity)
        builder.setView(dialogView)
        builder.setCancelable(false)
        isdialog = builder.create()
        isdialog.show()
    }
    fun isDismiss()
    {
        isdialog.dismiss()
    }
}