package com.oldpad.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.oldpad.app.R
import com.oldpad.app.data.Tile
import com.oldpad.app.data.TileType
import com.oldpad.app.databinding.ItemManageBinding

data class ManageItem(val tile: Tile, val visible: Boolean)

class ManageAdapter(
    private val onMoveUp: (Int) -> Unit,
    private val onMoveDown: (Int) -> Unit,
    private val onToggle: (Int, Boolean) -> Unit
) : RecyclerView.Adapter<ManageAdapter.Holder>() {

    private val items = ArrayList<ManageItem>()

    fun submit(list: List<ManageItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class Holder(val binding: ItemManageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemManageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val binding = holder.binding
        val context = binding.root.context

        binding.title.text = item.tile.title
        val type = when (item.tile.type) {
            TileType.VIDEO -> R.string.type_video
            TileType.ALBUM -> R.string.type_album
            TileType.GAME -> R.string.type_game
        }
        binding.type.text = context.getString(type) + " · " + item.tile.subtitle

        binding.btnUp.alpha = if (position == 0) 0.35f else 1f
        binding.btnUp.isEnabled = position != 0
        binding.btnUp.setOnClickListener { onMoveUp(position) }

        binding.btnDown.alpha = if (position == items.size - 1) 0.35f else 1f
        binding.btnDown.isEnabled = position != items.size - 1
        binding.btnDown.setOnClickListener { onMoveDown(position) }

        binding.switchVisible.setOnCheckedChangeListener(null)
        binding.switchVisible.isChecked = item.visible
        binding.switchVisible.setOnCheckedChangeListener { _, checked ->
            onToggle(position, checked)
        }

        binding.root.alpha = if (item.visible) 1f else 0.55f
    }
}
