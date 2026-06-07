package org.levimc.launcher.ui.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import org.levimc.launcher.R
import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager
import android.text.Html
import java.io.File

class CosmosActivity : BaseActivity() {

    private lateinit var settingsItemsContainer: LinearLayout
    private lateinit var manager: InbuiltModManager

    companion object {
        const val COSMOS_VERSION = "1.1.0"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cosmos)

        findViewById<TextView>(R.id.tv_cosmos_version).text =
            getString(R.string.cosmos_version, COSMOS_VERSION)

        settingsItemsContainer = findViewById(R.id.cosmos_settings_container)
        manager = InbuiltModManager.getInstance(this)

        addSwitchItem(
            label = "News feed",
            defChecked = manager.isNewsEnabled,
            iconRes = R.drawable.ic_news,
        ) { _, isChecked ->
            manager.setNews(isChecked)
            Toast.makeText(this, "News Disabled: $isChecked", Toast.LENGTH_SHORT).show()
        }

        addButtonItem("Reset News Data") {
            resetNewsData()
        }

        val changelogTv = findViewById<TextView>(R.id.tv_cosmos_changelog)
        if (changelogTv != null) {
            val git = org.levimc.launcher.core.mods.inbuilt.cosmos.CosmosResponsesGit(this)
            val changelogText = git.changelog
            if (changelogText.isNullOrEmpty()) {
                changelogTv.text = "No changelog available."
            } else {
                val formattedText = changelogText
                    .replace("\r\n", "<br/>")
                    .replace("\n", "<br/>")
                    .replace(Regex("\\*\\*(.*?)\\*\\*"), "<b>$1</b>")

                changelogTv.text = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    Html.fromHtml(formattedText, Html.FROM_HTML_MODE_COMPACT)
                } else {
                    @Suppress("DEPRECATION")
                    Html.fromHtml(formattedText)
                }
            }
        }
    }

    private fun resetNewsData() {
        val customJsonsDir = File(filesDir, "customJsons")
        val miscDir = File(filesDir, "misc")

        val newsFile = File(customJsonsDir, "News.json")
        val newsHistoryFile = File(miscDir, "NewsHistory.json")

        var deletedNews = false
        var deletedHistory = false

        if (newsFile.exists()) {
            deletedNews = newsFile.delete()
        }
        if (newsHistoryFile.exists()) {
            deletedHistory = newsHistoryFile.delete()
        }

        if (deletedNews || deletedHistory) {
            Toast.makeText(this, "News cache successfully cleared!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No local news data found to clear.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addSwitchItem(
        label: String,
        defChecked: Boolean,
        iconRes: Int? = null,
        listener: ((CompoundButton, Boolean) -> Unit)?
    ) {
        val ll = LayoutInflater.from(this)
            .inflate(R.layout.item_settings_switch, settingsItemsContainer, false)
        ll.findViewById<TextView>(R.id.tv_title).text = label

        val iv = ll.findViewById<ImageView>(R.id.iv_icon)
        if (iconRes != null) {
            iv.setImageResource(iconRes)
            iv.visibility = View.VISIBLE
        }

        val sw = ll.findViewById<SwitchMaterial>(R.id.switch_value)
        sw.isChecked = defChecked
        listener?.let { sw.setOnCheckedChangeListener(it) }
        settingsItemsContainer.addView(ll)
    }

    private fun addButtonItem(label: String, onClick: () -> Unit) {
        val button = MaterialButton(this).apply {
            text = label
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (16 * resources.displayMetrics.density).toInt()
            }
            setOnClickListener { onClick() }
        }
        settingsItemsContainer.addView(button)
    }
}