package au.com.shiftyjelly.pocketcasts.filters

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import au.com.shiftyjelly.pocketcasts.filters.databinding.FragmentCreateContainerBinding
import au.com.shiftyjelly.pocketcasts.models.entity.SmartPlaylist
import au.com.shiftyjelly.pocketcasts.ui.extensions.getThemeColor
import au.com.shiftyjelly.pocketcasts.ui.extensions.getTintedDrawable
import au.com.shiftyjelly.pocketcasts.ui.extensions.setupKeyboardModePan
import au.com.shiftyjelly.pocketcasts.ui.extensions.setupKeyboardModeResize
import au.com.shiftyjelly.pocketcasts.ui.extensions.themeColors
import au.com.shiftyjelly.pocketcasts.ui.helper.ColorUtils
import au.com.shiftyjelly.pocketcasts.ui.helper.FragmentHostListener
import au.com.shiftyjelly.pocketcasts.ui.helper.StatusBarIconColor
import au.com.shiftyjelly.pocketcasts.ui.theme.ThemeColor
import au.com.shiftyjelly.pocketcasts.views.extensions.includeStatusBarPadding
import au.com.shiftyjelly.pocketcasts.views.extensions.setSystemWindowInsetToPadding
import au.com.shiftyjelly.pocketcasts.views.fragments.BaseFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import au.com.shiftyjelly.pocketcasts.images.R as IR
import au.com.shiftyjelly.pocketcasts.localization.R as LR
import au.com.shiftyjelly.pocketcasts.ui.R as UR

@AndroidEntryPoint
class CreateFilterContainerFragment : BaseFragment() {
    companion object {
        fun newInstance(): CreateFilterContainerFragment {
            return CreateFilterContainerFragment()
        }
    }

    private var _binding: FragmentCreateContainerBinding? = null
    private val binding get() = _binding!!
    val viewModel: CreateFilterViewModel by activityViewModels()
    private var playlistSaved = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCreateContainerBinding.inflate(inflater, container, false)
        binding.root.setSystemWindowInsetToPadding(bottom = true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = CreatePagerAdapter(childFragmentManager, viewLifecycleOwner.lifecycle)
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateToolbarColors()

                when (position) {
                    0 -> {
                        binding.toolbar.title = ""
                        binding.btnCreate.text = getString(LR.string.navigation_continue)
                        binding.btnCreate.isEnabled = !adapter.lockedToFirstPage
                        binding.toolbar.setNavigationOnClickListener {
                            @Suppress("DEPRECATION")
                            activity?.onBackPressed()
                        }
                    }
                    1 -> {
                        binding.toolbar.title = getString(LR.string.filters_create_filter_details)
                        binding.btnCreate.text = getString(LR.string.filters_create_save_filter)
                        binding.btnCreate.isEnabled = viewModel.filterName.value.isNotEmpty()
                        binding.toolbar.setNavigationOnClickListener { binding.viewPager.setCurrentItem(0, true) }
                    }
                }
            }
        })
        binding.viewPager.isUserInputEnabled = false
        binding.toolbar.includeStatusBarPadding()

        binding.btnCreate.setOnClickListener {
            if (binding.viewPager.currentItem == 0) {
                binding.viewPager.setCurrentItem(1, true)
            } else {
                viewModel.saveNewFilterDetails()
                playlistSaved = true
                (activity as? FragmentHostListener)?.closeModal(this)
            }
        }

        launch {
            viewModel.setup(null)
            binding.viewPager.adapter = adapter
            observeColorIndex()
            observeFilterName()
            observeLockedFirstPage(adapter)
            observePlaylist(adapter)
        }
    }

    private fun updateToolbarColors() {
        val colorResId = SmartPlaylist.themeColors.getOrNull(viewModel.colorIndex.value) ?: UR.attr.filter_01
        val tintColor = view?.context?.getThemeColor(colorResId) ?: return
        val iconRes = if (binding.viewPager.currentItem == 0) IR.drawable.ic_close else IR.drawable.ic_arrow_back
        val backIcon = context?.getTintedDrawable(iconRes, ThemeColor.filterIcon01(theme.activeTheme, tintColor))
        val toolbar = binding.toolbar
        toolbar.navigationIcon = backIcon

        val filterUi01 = ThemeColor.filterUi01(theme.activeTheme, tintColor)
        toolbar.setBackgroundColor(filterUi01)
        toolbar.setTitleTextColor(ThemeColor.filterText01(theme.activeTheme, tintColor))
        theme.updateWindowStatusBarIcons(window = requireActivity().window, statusBarIconColor = StatusBarIconColor.Theme)
    }

    private fun observeLockedFirstPage(adapter: CreatePagerAdapter) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.lockedToFirstPage.collect {
                    adapter.lockedToFirstPage = it
                }
            }
        }
    }

    private fun observePlaylist(adapter: CreatePagerAdapter) {
        viewModel.smartPlaylist?.observe(viewLifecycleOwner) {
            binding.btnCreate.isEnabled = !adapter.lockedToFirstPage
        }
    }

    private fun observeFilterName() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.filterName.collect {
                    if (binding.viewPager.currentItem == 1) {
                        binding.btnCreate.isEnabled = it.isNotEmpty()
                    }
                }
            }
        }
    }

    private fun observeColorIndex() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.colorIndex.collect {
                    val colorResId = SmartPlaylist.themeColors.getOrNull(it) ?: UR.attr.filter_01
                    val tintColor = requireContext().getThemeColor(colorResId)
                    val stateList = ColorStateList(
                        arrayOf(
                            intArrayOf(android.R.attr.state_enabled),
                            intArrayOf(),
                        ),
                        intArrayOf(
                            tintColor,
                            ColorUtils.colorWithAlpha(tintColor, 76),
                        ),
                    )
                    binding.btnCreate.backgroundTintList = stateList

                    updateToolbarColors()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        setupKeyboardModeResize()
    }

    override fun onDetach() {
        super.onDetach()
        setupKeyboardModePan()

        if (!playlistSaved) {
            viewModel.clearNewFilter()
        }
    }

    override fun onBackPressed(): Boolean {
        if (binding.viewPager.currentItem > 0) {
            binding.viewPager.setCurrentItem(binding.viewPager.currentItem - 1, true)
            return true
        }

        return super.onBackPressed()
    }
}

private class CreatePagerAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle) : FragmentStateAdapter(fragmentManager, lifecycle) {
    var lockedToFirstPage = true
        set(value) {
            field = value
            notifyItemChanged(1)
        }

    override fun getItemCount(): Int {
        return if (lockedToFirstPage) 1 else 2
    }

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> CreateFilterChipFragment.newInstance()
            1 -> CreateFilterFragment.newInstance(CreateFilterFragment.Mode.Create)
            else -> throw IllegalStateException("Unknown position in create filter adapter")
        }
    }
}
