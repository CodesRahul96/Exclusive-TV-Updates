package com.codesrahul.exclusivetv

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.codesrahul.exclusivetv.databinding.MenuBinding
import com.codesrahul.exclusivetv.models.TVList
import com.codesrahul.exclusivetv.models.TVListModel
import com.codesrahul.exclusivetv.models.TVModel

class MenuFragment : Fragment(), GroupAdapter.ItemListener, ListAdapter.ItemListener {
    private var _binding: MenuBinding? = null
    private val binding get() = _binding!!

    private lateinit var groupAdapter: GroupAdapter
    private lateinit var listAdapter: ListAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.i(TAG, "onViewCreated")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        _binding = MenuBinding.inflate(inflater, container, false)

        groupAdapter = GroupAdapter(
            context,
            binding.group,
            TVList.groupModel,
        )
        binding.group.adapter = groupAdapter
        binding.group.layoutManager =
            LinearLayoutManager(context)
        groupAdapter.setItemListener(this)
        groupAdapter.attachItemTouchHelper()

        val currentPos = TVList.groupModel.position.value ?: 0
        var tvListModel = TVList.groupModel.getTVListModel(currentPos)
        if (tvListModel == null) {
            TVList.groupModel.setPosition(0)
            tvListModel = TVList.groupModel.getTVListModel(0)
        }

        listAdapter = ListAdapter(
            requireContext(),
            binding.list,
            tvListModel ?: TVList.groupModel.getTVListModel(0)!!,
        )
        binding.list.adapter = listAdapter
        binding.list.layoutManager =
            LinearLayoutManager(context)
        listAdapter.focusable(false)
        listAdapter.setItemListener(this)
        listAdapter.attachItemTouchHelper()

        binding.btnGridGuide.setOnClickListener {
            (activity as? MainActivity)?.showEpgGrid()
        }

        return binding.root
    }

    fun update() {
        if (!::groupAdapter.isInitialized) return
        groupAdapter.update(TVList.groupModel)

        val currentPos = TVList.groupModel.position.value ?: 0
        var tvListModel = TVList.groupModel.getTVListModel(currentPos)
        if (tvListModel == null) {
            TVList.groupModel.setPosition(0)
            tvListModel = TVList.groupModel.getTVListModel(0)
        }

        if (tvListModel != null) {
            (binding.list.adapter as ListAdapter).update(tvListModel)
        }
    }

    fun updateList(position: Int) {
        TVList.groupModel.setPosition(position)
        SP.positionGroup = position
        val tvListModel = TVList.groupModel.getTVListModel()
        Log.i(TAG, "updateList tvListModel $position ${tvListModel?.size()}")
        if (tvListModel != null) {
            (binding.list.adapter as ListAdapter).update(tvListModel)
        }
    }

    private fun hideSelf() {
        requireActivity().supportFragmentManager.beginTransaction()
            .hide(this)
            .commit()
    }

    private val updateHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingTvListModel: TVListModel? = null
    private val updateRunnable = Runnable {
        pendingTvListModel?.let {
            (binding.list.adapter as ListAdapter).update(it)
        }
    }

    override fun onItemFocusChange(tvListModel: TVListModel, hasFocus: Boolean) {
        if (hasFocus) {
            // Cancel any pending update
            updateHandler.removeCallbacks(updateRunnable)
            
            pendingTvListModel = tvListModel
            // Debounce update by 250ms
            updateHandler.postDelayed(updateRunnable, 250)
        }
    }

    override fun onItemClicked(position: Int) {
    }

    override fun onItemFocusChange(tvModel: TVModel, hasFocus: Boolean) {
    }

    override fun onItemClicked(tvModel: TVModel) {
        TVList.setPosition(tvModel.tv.id)
        (activity as MainActivity).hideMenuFragment()
    }

    override fun onKey(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (listAdapter.itemCount == 0) {
                    Toast.makeText(context, "No channel yet", Toast.LENGTH_LONG).show()
                    return true
                }
                groupAdapter.focusable(false)
                listAdapter.focusable(true)

                val tvModel = TVList.getTVModel()
                if (tvModel != null) {
                    listAdapter.toPosition(tvModel.listIndex)

                    if (tvModel.groupIndex == TVList.groupModel.position.value!!) {
                        Log.i(
                            TAG,
                            "list on show toPosition ${tvModel.tv.title} ${tvModel.listIndex}/${listAdapter.tvListModel.size()}"
                        )
                        listAdapter.toPosition(tvModel.listIndex)
                    } else {
                        listAdapter.toPosition(0)
                    }
                } else {
                    listAdapter.toPosition(0)
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (binding.btnGridGuide.hasFocus()) return true // Top reached
            }
        }
        return false
    }

    override fun onKey(listAdapter: ListAdapter, keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                groupAdapter.focusable(true)
                listAdapter.focusable(false)
                listAdapter.clear()
                Log.i(TAG, "group toPosition on left")
                groupAdapter.toPosition(TVList.groupModel.position.value!!)
                return true
            }
//            KeyEvent.KEYCODE_DPAD_RIGHT -> {
//                binding.group.visibility = VISIBLE
//                groupAdapter.focusable(true)
//                listAdapter.focusable(false)
//                listAdapter.clear()
//                Log.i(TAG, "group toPosition on left")
//                groupAdapter.toPosition(TVList.groupModel.position.value!!)
//                return true
//            }
        }
        return false
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!::groupAdapter.isInitialized || !::listAdapter.isInitialized) {
            return
        }
        if (!hidden) {
            if (binding.list.isVisible) {

                val currentTvModel = TVList.getTVModel()
                if (currentTvModel != null) {
                    val groupIndex = currentTvModel.groupIndex
                    Log.i(
                        TAG,
                        "groupIndex $groupIndex ${TVList.groupModel.position.value!!}"
                    )

                    if (groupIndex == TVList.groupModel.position.value!!) {
                        if (listAdapter.tvListModel.getIndex() != currentTvModel.groupIndex) {
                            updateList(groupIndex)
                        }

                        Log.i(
                            TAG,
                            "list on show toPosition ${currentTvModel.tv.title} ${currentTvModel.listIndex}/${listAdapter.tvListModel.size()}"
                        )
                        listAdapter.toPosition(currentTvModel.listIndex)
                    } else {
                        listAdapter.toPosition(0)
                    }
                }
            }
            if (binding.group.isVisible) {
                Log.i(
                    TAG,
                    "group on show toPosition ${TVList.groupModel.position.value!!}/${TVList.groupModel.size()}"
                )
                groupAdapter.toPosition(TVList.groupModel.position.value!!)
            }
        } else {
            view?.post {
                if (::groupAdapter.isInitialized) groupAdapter.visible = false
                if (::listAdapter.isInitialized) listAdapter.visible = false
            }
        }
    }

    override fun onResume() {
        super.onResume()
//        groupAdapter.toPosition(TVList.groupModel.position.value!!)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "MenuFragment"
    }
}
