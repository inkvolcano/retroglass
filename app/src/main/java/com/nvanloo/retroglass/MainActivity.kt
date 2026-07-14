package com.nvanloo.retroglass

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.nvanloo.retroglass.model.BiosCatalog
import com.nvanloo.retroglass.model.RomEntry
import com.nvanloo.retroglass.model.RomLibrary

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: RomAdapter
    private lateinit var emptyView: TextView
    private var searchQuery = ""
    private val history by lazy { com.nvanloo.retroglass.model.GameHistory(this) }

    private val pickRoms = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        val result = RomLibrary.importAll(this, uris)
        val message = buildString {
            if (result.imported.isNotEmpty()) {
                append(getString(R.string.imported_count, result.imported.size))
            }
            if (result.skipped.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append(getString(R.string.skipped_files, result.skipped.joinToString(", ")))
            }
        }
        if (message.isNotEmpty()) Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        refresh()
    }

    private val pickBios = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val name = RomLibrary.importBios(this, uri)
        Toast.makeText(
            this,
            if (name != null) getString(R.string.bios_imported, name)
            else getString(R.string.bios_import_failed),
            Toast.LENGTH_LONG,
        ).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#141419"))
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 28f
            setTextColor(Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val subtitle = TextView(this).apply {
            text = getString(R.string.main_subtitle)
            textSize = 13f
            setTextColor(Color.parseColor("#99FFFFFF"))
        }

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val topMargin = (12 * resources.displayMetrics.density).toInt()
            setPadding(0, topMargin, 0, topMargin)
        }
        val addButton = MaterialButton(this).apply {
            text = getString(R.string.add_roms)
            setOnClickListener { pickRoms.launch(arrayOf("*/*")) }
        }
        val biosButton = MaterialButton(
            this, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle,
        ).apply {
            text = getString(R.string.import_bios)
            setOnClickListener { showBiosStatus() }
        }
        buttonRow.addView(addButton)
        buttonRow.addView(View(this), LinearLayout.LayoutParams((8 * resources.displayMetrics.density).toInt(), 1))
        buttonRow.addView(biosButton)

        emptyView = TextView(this).apply {
            text = getString(R.string.empty_library)
            setTextColor(Color.parseColor("#77FFFFFF"))
            textSize = 14f
            gravity = Gravity.CENTER
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad * 2, pad, pad)
        }

        val searchBox = android.widget.EditText(this).apply {
            hint = getString(R.string.search_games)
            setHintTextColor(Color.parseColor("#66FFFFFF"))
            setTextColor(Color.WHITE)
            textSize = 15f
            maxLines = 1
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.parseColor("#232330"))
            }
            val p = (12 * resources.displayMetrics.density).toInt()
            setPadding(p + p / 2, p, p, p)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    searchQuery = s?.toString()?.trim().orEmpty()
                    refresh()
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }

        adapter = RomAdapter(
            onClick = { entry ->
                history.recordPlayed(entry.file.absolutePath)
                EmulationActivity.launch(this, entry.file.absolutePath, entry.console)
            },
            onLongClick = { entry -> showGameOptions(entry) },
        )
        val recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(buttonRow)
        root.addView(
            searchBox,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = (10 * resources.displayMetrics.density).toInt() },
        )
        root.addView(emptyView)
        root.addView(
            recycler,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val roms = RomLibrary.scan(this)
        val q = searchQuery.lowercase()
        val filtered = if (q.isEmpty()) roms else roms.filter { it.displayName.lowercase().contains(q) }
        val byPath = filtered.associateBy { it.file.absolutePath }
        val rows = mutableListOf<Row>()

        // Favorites (in their console order for stability)
        val favs = filtered.filter { history.isFavorite(it.file.absolutePath) }
        if (favs.isNotEmpty()) {
            rows.add(Row.Header(getString(R.string.section_favorites, favs.size)))
            favs.sortedBy { it.displayName.lowercase() }.forEach { rows.add(Row.Game(it)) }
        }

        // Recently played (most recent first)
        val recents = history.recents().mapNotNull { byPath[it] }.take(8)
        if (recents.isNotEmpty()) {
            rows.add(Row.Header(getString(R.string.section_recent)))
            recents.forEach { rows.add(Row.Game(it)) }
        }

        // Everything, grouped by system
        val byConsole = filtered.groupBy { it.console }
        for (console in com.nvanloo.retroglass.model.Console.entries) {
            val games = byConsole[console] ?: continue
            rows.add(Row.Header("${console.displayName}  ·  ${games.size}"))
            games.sortedBy { it.displayName.lowercase() }.forEach { rows.add(Row.Game(it)) }
        }
        adapter.submit(rows)
        emptyView.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showGameOptions(entry: RomEntry) {
        val path = entry.file.absolutePath
        val favLabel = getString(
            if (history.isFavorite(path)) R.string.unfavorite else R.string.favorite,
        )
        val options = arrayOf(
            favLabel,
            getString(R.string.change_system),
            getString(R.string.delete),
        )
        AlertDialog.Builder(this)
            .setTitle(entry.displayName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { history.toggleFavorite(path); refresh() }
                    1 -> chooseSystem(entry)
                    2 -> confirmDelete(entry)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun chooseSystem(entry: RomEntry) {
        val consoles = RomLibrary.candidateConsoles(entry)
        val names = consoles.map { it.displayName }.toTypedArray()
        val current = consoles.indexOf(entry.console).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.change_system)
            .setSingleChoiceItems(names, current) { dialog, which ->
                RomLibrary.moveToConsole(this, entry, consoles[which])
                dialog.dismiss()
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showBiosStatus() {
        val statuses = BiosCatalog.status(this)
        val text = buildString {
            for (s in statuses) {
                append(if (s.present) "✓  " else "✗  ")
                append(s.system)
                append('\n')
                if (!s.present) append("      needs ${s.filenames} — ${s.note}\n")
            }
        }.trim()
        AlertDialog.Builder(this)
            .setTitle(R.string.bios_status_title)
            .setMessage(text)
            .setPositiveButton(R.string.import_bios_file) { _, _ -> pickBios.launch(arrayOf("*/*")) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(entry: RomEntry) {
        AlertDialog.Builder(this)
            .setTitle(entry.displayName)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                RomLibrary.delete(entry)
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ------------------------------------------------------------ adapter

    sealed class Row {
        data class Header(val title: String) : Row()
        data class Game(val entry: RomEntry) : Row()
    }

    private class RomAdapter(
        val onClick: (RomEntry) -> Unit,
        val onLongClick: (RomEntry) -> Unit,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var rows: List<Row> = emptyList()

        @Suppress("NotifyDataSetChanged")
        fun submit(list: List<Row>) {
            rows = list
            notifyDataSetChanged()
        }

        override fun getItemViewType(position: Int): Int =
            if (rows[position] is Row.Header) TYPE_HEADER else TYPE_GAME

        override fun getItemCount() = rows.size

        class HeaderHolder(val text: TextView) : RecyclerView.ViewHolder(text)
        class GameHolder(val row: LinearLayout, val chip: TextView, val name: TextView) :
            RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val ctx = parent.context
            val density = ctx.resources.displayMetrics.density
            if (viewType == TYPE_HEADER) {
                val tv = TextView(ctx).apply {
                    textSize = 13f
                    setTextColor(Color.parseColor("#8F8FB0"))
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    val padH = (12 * density).toInt()
                    setPadding(padH, (18 * density).toInt(), padH, (6 * density).toInt())
                    layoutParams = RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                }
                return HeaderHolder(tv)
            }
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val pad = (12 * density).toInt()
                setPadding(pad, pad, pad, pad)
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            val chip = TextView(ctx).apply {
                textSize = 11f
                setTextColor(Color.WHITE)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                val padH = (10 * density).toInt()
                val padV = (5 * density).toInt()
                setPadding(padH, padV, padH, padV)
            }
            val name = TextView(ctx).apply {
                textSize = 16f
                setTextColor(Color.WHITE)
                setPadding((14 * density).toInt(), 0, 0, 0)
            }
            row.addView(chip)
            row.addView(name)
            return GameHolder(row, chip, name)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val r = rows[position]) {
                is Row.Header -> (holder as HeaderHolder).text.text = r.title
                is Row.Game -> {
                    val h = holder as GameHolder
                    val entry = r.entry
                    h.name.text = entry.displayName
                    h.chip.text = entry.console.displayName
                    h.chip.background = GradientDrawable().apply {
                        cornerRadius = 24f
                        setColor(entry.console.accentColor)
                    }
                    h.row.setOnClickListener { onClick(entry) }
                    h.row.setOnLongClickListener { onLongClick(entry); true }
                }
            }
        }

        companion object {
            private const val TYPE_HEADER = 0
            private const val TYPE_GAME = 1
        }
    }
}
