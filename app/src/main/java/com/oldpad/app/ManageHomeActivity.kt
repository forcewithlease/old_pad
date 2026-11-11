package com.oldpad.app

import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.oldpad.app.data.HomeConfig
import com.oldpad.app.data.Tile
import com.oldpad.app.data.TileRepository
import com.oldpad.app.databinding.ActivityManageBinding
import com.oldpad.app.ui.ManageAdapter
import com.oldpad.app.ui.ManageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 给家人用的“管理后台”：决定首页显示哪些格子、谁前谁后。 */
class ManageHomeActivity : BaseActivity() {

    private lateinit var binding: ActivityManageBinding
    private lateinit var adapter: ManageAdapter

    private val tiles = mutableListOf<Tile>()
    private val hidden = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemInsets()

        binding.btnBack.setOnClickListener { finish() }

        adapter = ManageAdapter(
            onMoveUp = { position -> move(position, -1) },
            onMoveDown = { position -> move(position, 1) },
            onToggle = { position, checked -> toggle(position, checked) }
        )
        binding.manageList.layoutManager = LinearLayoutManager(this)
        binding.manageList.adapter = adapter

        binding.btnReset.setOnClickListener {
            HomeConfig.reset(this)
            hidden.clear()
            load()
            Toast.makeText(this, R.string.order_reset, Toast.LENGTH_SHORT).show()
        }

        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val all = withContext(Dispatchers.IO) {
                TileRepository.all(this@ManageHomeActivity)
            }
            tiles.clear()
            tiles.addAll(orderTiles(all))
            hidden.clear()
            hidden.addAll(HomeConfig.hidden(this@ManageHomeActivity))

            if (tiles.isEmpty()) {
                Toast.makeText(
                    this@ManageHomeActivity,
                    R.string.no_manage_items,
                    Toast.LENGTH_LONG
                ).show()
            }
            render()
        }
    }

    private fun orderTiles(all: List<Tile>): List<Tile> {
        val remaining = LinkedHashMap<String, Tile>()
        all.forEach { remaining[it.id] = it }

        val result = ArrayList<Tile>(all.size)
        for (id in HomeConfig.order(this)) {
            remaining.remove(id)?.let { result.add(it) }
        }
        result.addAll(remaining.values)
        return result
    }

    private fun render() {
        adapter.submit(tiles.map { ManageItem(it, it.id !in hidden) })
    }

    private fun move(position: Int, delta: Int) {
        val target = position + delta
        if (position !in tiles.indices || target !in tiles.indices) return
        val item = tiles.removeAt(position)
        tiles.add(target, item)
        HomeConfig.saveOrder(this, tiles.map { it.id })
        render()
    }

    private fun toggle(position: Int, visible: Boolean) {
        val tile = tiles.getOrNull(position) ?: return
        if (visible) hidden.remove(tile.id) else hidden.add(tile.id)
        HomeConfig.setHidden(this, hidden)
        render()
    }
}
