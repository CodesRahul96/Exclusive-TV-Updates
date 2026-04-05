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
    private var isInitializing = false
    var isSyncing = false

     override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
         super.onViewCreated(view, savedInstanceState)
         
         // Observe global model changes to keep menu in sync even when visible
         TVList.groupModel.change.observe(viewLifecycleOwner) {
             if (it == true) {
                 update()
             }
         }
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
        
        // Performance optimizations for TV D-Pad scrolling
        binding.group.setHasFixedSize(true)
        binding.group.setItemViewCacheSize(15)
        binding.group.itemAnimator = null // Prevent DiffUtil flicker during fast scroll
        
        groupAdapter.setItemListener(this)
        groupAdapter.attachItemTouchHelper()

        val currentPos = TVList.groupModel.position.value ?: 0
        val tvListModel = TVList.groupModel.getTVListModel(currentPos) ?: TVList.groupModel.getTVListModel(0)
        
        if (tvListModel == null) {
            // This should ideally never happen as group 0 is local, but we must protect it
            return binding.root 
        }

        listAdapter = ListAdapter(
            context,
            binding.list,
            tvListModel,
        )
        binding.list.adapter = listAdapter
        binding.list.layoutManager =
            LinearLayoutManager(context)
            
        // Performance optimizations for TV D-Pad scrolling
        binding.list.setHasFixedSize(true)
        binding.list.setItemViewCacheSize(25)
        binding.list.itemAnimator = null // Prevent DiffUtil flicker during fast scroll
        
        listAdapter.focusable(false)
        listAdapter.setItemListener(this)
        listAdapter.attachItemTouchHelper()

//        binding.btnGridGuide.setOnClickListener {
//            (activity as? MainActivity)?.showEpgGrid()
//        }

        binding.root.setOnClickListener {
            hideSelf()
        }

        return binding.root
    }

    fun update() {
        if (_binding == null || !::groupAdapter.isInitialized) return
        groupAdapter.update(TVList.groupModel)

        val currentPos = TVList.groupModel.position.value ?: 0
        var tvListModel = TVList.groupModel.getTVListModel(currentPos)
        if (tvListModel == null) {
            TVList.groupModel.setPosition(0)
            tvListModel = TVList.groupModel.getTVListModel(0)
        }

        if (tvListModel != null && _binding != null) {
            val listAdapter = (binding.list.adapter as ListAdapter)
            listAdapter.update(tvListModel)
            
            // Toggle empty state
            binding.emptyState.visibility = if (tvListModel.size() == 0) View.VISIBLE else View.GONE
        }
    }

    fun updateList(position: Int, onComplete: (() -> Unit)? = null) {
        TVList.groupModel.setPosition(position)
        SP.positionGroup = position
        val tvListModel = TVList.groupModel.getTVListModel()
        if (tvListModel != null) {
            (binding.list.adapter as ListAdapter).update(tvListModel, onComplete)
        }
    }

    private fun hideSelf() {
        val fm = activity?.supportFragmentManager ?: return
        fm.beginTransaction()
            .hide(this)
            .commitAllowingStateLoss()
    }

    private val updateHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingTvListModel: TVListModel? = null
    private val updateRunnable = Runnable {
        val binding = _binding ?: return@Runnable
        pendingTvListModel?.let {
            (binding.list.adapter as ListAdapter).update(it)
        }
    }


    override fun onItemFocusChange(tvListModel: TVListModel, hasFocus: Boolean) {
        if (isInitializing || isSyncing) return
        if (hasFocus) {
            // Toggle empty state immediately for the focused category
            binding.emptyState.visibility = if (tvListModel.size() == 0) View.VISIBLE else View.GONE

            // Cancel any pending update
            updateHandler.removeCallbacks(updateRunnable)
            
            pendingTvListModel = tvListModel
            // Debounce update by 150ms
            updateHandler.postDelayed(updateRunnable, 150)
        }
    }

    override fun onItemClicked(position: Int) {
    }

    override fun onItemFocusChange(tvModel: TVModel, hasFocus: Boolean) {
    }

    override fun onItemClicked(tvModel: TVModel) {
        TVList.setPositionByModel(tvModel)
        (activity as? MainActivity)?.hideMenuFragment()
    }

    override fun onKey(keyCode: Int): Boolean {
        when (keyCode) {
             KeyEvent.KEYCODE_DPAD_RIGHT -> {
                 if (listAdapter.itemCount == 0) {
                     // If update is pending (debounced), force it immediately if it contains channels
                     val pendingSize = pendingTvListModel?.size() ?: 0
                     if (pendingSize > 0) {
                         updateHandler.removeCallbacks(updateRunnable)
                         listAdapter.update(pendingTvListModel!!)
                         // Note: listAdapter.itemCount still won't update until DiffUtil finishes,
                         // but we can proceed with navigation assuming it will be ready shortly.
                     } else {
                         Toast.makeText(context, "No channel yet", Toast.LENGTH_LONG).show()
                         return true
                     }
                 }
                groupAdapter.stopMove() // End category move if active
                groupAdapter.focusable(false)
                listAdapter.focusable(true)

                val tvModel = TVList.getTVModel()
                if (tvModel != null) {
                    val currentGroupPosition = TVList.groupModel.position.value ?: 0
                    if (tvModel.groupIndex == currentGroupPosition) {
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
                listAdapter.stopMove() // End channel move if active
                groupAdapter.focusable(true)
                listAdapter.focusable(false)
                listAdapter.clear()
                groupAdapter.toPosition(TVList.groupModel.position.value ?: 0)
                return true
            }
//            KeyEvent.KEYCODE_DPAD_RIGHT -> {
//                binding.group.visibility = VISIBLE
//                groupAdapter.focusable(true)
//                listAdapter.focusable(false)
//                listAdapter.clear()
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
            // 1. Lock both adapters immediately
            isInitializing = true
            isSyncing = true
            groupAdapter.isSyncing = true
            listAdapter.isSyncing = true
            groupAdapter.isFocusLocked = true
            listAdapter.isFocusLocked = true

            // MOBILE STABILITY: Wait for fragment transition animation to finish
            view?.postDelayed({
                if (isHidden) return@postDelayed // Safety check
                
                // INTELLIGENT SYNC: Match menu category to currently playing channel
                val currentTvModel = TVList.currentPlayingModel.value ?: TVList.getTVModel()
                
                if (currentTvModel != null) {
                    val playingGroupIndex = currentTvModel.groupIndex
                    
                    // 2. Set default focus indices
                    groupAdapter.setDefaultFocus(playingGroupIndex)
                    
                    // Force update UI model
                    TVList.groupModel.setPosition(playingGroupIndex)
                    SP.positionGroup = playingGroupIndex
                    
                    updateList(playingGroupIndex) {
                        listAdapter.setDefaultFocus(currentTvModel.listIndex)

                        // 3. Synchronized scroll chain
                        view?.post {
                            groupAdapter.toPosition(playingGroupIndex) {
                                listAdapter.toPosition(currentTvModel.listIndex) {
                                    isSyncing = false
                                    groupAdapter.isSyncing = false
                                    listAdapter.isSyncing = false
                                    isInitializing = false
                                }
                            }
                        }
                    }
                } else {
                    // Default fallback
                    val lastPos = TVList.groupModel.position.value ?: 0
                    groupAdapter.setDefaultFocus(lastPos)
                    view?.post {
                        groupAdapter.toPosition(lastPos) {
                            if (listAdapter.itemCount > 0) {
                                listAdapter.setDefaultFocus(0)
                                listAdapter.toPosition(0) {
                                    isSyncing = false
                                    groupAdapter.isSyncing = false
                                    listAdapter.isSyncing = false
                                    isInitializing = false
                                }
                            } else {
                                listAdapter.isFocusLocked = false
                                isSyncing = false
                                groupAdapter.isSyncing = false
                                listAdapter.isSyncing = false
                                isInitializing = false
                            }
                        }
                    }
                }
            }, 250) // 250ms quiescence delay for mobile transitions
            
            groupAdapter.visible = true
            listAdapter.visible = true
        } else {
            if (::groupAdapter.isInitialized) groupAdapter.stopMove()
            if (::listAdapter.isInitialized) listAdapter.stopMove()
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
        updateHandler.removeCallbacksAndMessages(null)
        _binding = null
    }


    companion object {
        private const val TAG = "MenuFragment"
    }
}
