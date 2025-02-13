package org.anitab.mentorship.view.fragments

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityOptionsCompat
import androidx.core.util.Pair
import androidx.core.view.ViewCompat
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.fragment_members.*
import org.anitab.mentorship.R
import org.anitab.mentorship.models.User
import org.anitab.mentorship.utils.Constants
import org.anitab.mentorship.utils.Constants.FILTER_MAP
import org.anitab.mentorship.utils.Constants.FILTER_REQUEST_CODE
import org.anitab.mentorship.utils.Constants.SORT_KEY
import org.anitab.mentorship.utils.EndlessRecyclerScrollListener
import org.anitab.mentorship.view.activities.FilterActivity
import org.anitab.mentorship.view.activities.MainActivity
import org.anitab.mentorship.view.activities.MemberProfileActivity
import org.anitab.mentorship.view.adapters.MembersAdapter
import org.anitab.mentorship.viewmodels.MembersViewModel

/**
 * The fragment is responsible for showing all the members of the system in a list format
 */
class MembersFragment : BaseFragment() {

    companion object {
        /**
         * Creates an instance of [MembersFragment]
         */
        fun newInstance() = MembersFragment()
    }

    private var memberListInitialized = false

    private val membersViewModel: MembersViewModel by viewModels()
    private lateinit var rvAdapter: MembersAdapter
    private var isLoading = false
    private var isRecyclerView = false
    private var filterMap = hashMapOf(SORT_KEY to SortValues.REGISTRATION_DATE.name)

    override fun getLayoutResourceId(): Int = R.layout.fragment_members

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_members, menu)
        menu.findItem(R.id.search_item)?.let { searchItem ->
            val searchView = searchItem.actionView as SearchView
            searchView.queryHint = "Search members"
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextChange(newText: String): Boolean {
                    if (memberListInitialized)
                        searchUsers(newText)
                    return false
                }
                override fun onQueryTextSubmit(query: String): Boolean {
                    return false
                }
            })
        }
    }

    fun searchUsers(query: String) {
        val userList = arrayListOf<User>()
        for (user in membersViewModel.userList) {
            // ""+ to convert String to CharSequence
            if (("" + user.username).contains(query, ignoreCase = true)) {
                userList.add(user)
            }
        }
        rvMembers.apply {
            layoutManager = LinearLayoutManager(context)
            addLoadMoreListener(this)
            adapter = MembersAdapter(userList, ::openUserProfile)
        }
        tvEmptyList.visibility = View.GONE
    }
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setHasOptionsMenu(true)
        rvAdapter = MembersAdapter(arrayListOf<User>(), ::openUserProfile)
        srlMembers.setOnRefreshListener {
            if (!isLoading) {
                fetchNewest(true)
                isLoading = true
            }
        }

        membersViewModel.successful.observe(viewLifecycleOwner, { successful ->
            (activity as MainActivity).hideProgressDialog()
            srlMembers.isRefreshing = false
            isLoading = false
            pbMembers.visibility = View.INVISIBLE
            if (successful != null) {
                if (successful) {

                    rvAdapter.updateUsersList(filterMap, membersViewModel.userList)
                    if (membersViewModel.userList.isEmpty()) {
                        tvEmptyList.text = getString(R.string.empty_members_list)
                        rvMembers.visibility = View.GONE
                    } else {
                        if (!isRecyclerView) {
                            rvMembers.apply {
                                layoutManager = LinearLayoutManager(context)
                                adapter = MembersAdapter(membersViewModel.userList, ::openUserProfile)

                                addLoadMoreListener(this)
                                runLayoutAnimation(this)

                                val dividerItemDecoration = DividerItemDecoration(
                                        this.context, DividerItemDecoration.VERTICAL)
                                addItemDecoration(dividerItemDecoration)
                                adapter = rvAdapter
                                isRecyclerView = true
                            }
                        } else {
                            if (!filterMap["location"].isNullOrEmpty()) {

                                val hasUsersWithLocation = membersViewModel.userList.any {
                                    (it.location)?.contains(filterMap["location"]!!, ignoreCase = true) == true
                                }

                                if (!hasUsersWithLocation) {
                                    Toast.makeText(activity, getString(R.string.error_filter_not_found),
                                        Toast.LENGTH_SHORT).show()
                                }
                            }

                            if (!filterMap["skills"].isNullOrEmpty()) {

                                val hasUsersWithSkills = membersViewModel.userList.any {
                                    (it.skills)?.contains(filterMap["skills"]!!, ignoreCase = true) == true
                                }

                                if (!hasUsersWithSkills) {
                                    Toast.makeText(activity, getString(R.string.error_filter_not_found),
                                        Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        memberListInitialized = true
                        tvEmptyList.visibility = View.GONE
                    }
                } else {
                    view?.let {
                        Snackbar.make(it, membersViewModel.message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        })
        fetchNewest(true)
    }

    private fun runLayoutAnimation(recyclerView: RecyclerView) {
        val context = recyclerView.context
        recyclerView.layoutAnimation = AnimationUtils.loadLayoutAnimation(context,
                R.anim.layout_fall_down)
        recyclerView.adapter?.notifyDataSetChanged()
        recyclerView.scheduleLayoutAnimation()
    }

    private fun addLoadMoreListener(recyclerView: RecyclerView) {
        recyclerView.addOnScrollListener(object :
                EndlessRecyclerScrollListener(recyclerView.layoutManager as LinearLayoutManager) {
            override fun onLoadMore(page: Int, totalItemsCount: Int) {
                if (!isLoading) {
                    fetchNewest(false)
                    isLoading = true
                    pbMembers.visibility = View.VISIBLE
                }
            }
        })
    }

    private fun openUserProfile(memberId: Int, sharedImageView: ImageView, sharedTextView: TextView) {
        val intent = Intent(activity, MemberProfileActivity::class.java)
        intent.putExtra(Constants.MEMBER_USER_ID, memberId)
        val imgAnim = Pair.create<View, String>(sharedImageView,
                ViewCompat.getTransitionName(sharedImageView)!!)

        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(baseActivity, imgAnim)

        startActivity(intent, options.toBundle())
    }
    private val openUserProfile: (Int) -> Unit =
            { memberId ->
                val intent = Intent(activity, MemberProfileActivity::class.java)
                intent.putExtra(Constants.MEMBER_USER_ID, memberId)
                startActivity(intent)
            }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_filter -> {
                val intent = Intent(context, FilterActivity::class.java)
                intent.putExtra(FILTER_MAP, filterMap)
                startActivityForResult(intent, FILTER_REQUEST_CODE)
                activity?.overridePendingTransition(
                        R.anim.anim_slide_from_bottom,
                        R.anim.anim_stay)
                true
            }
            R.id.menu_refresh -> {
                fetchNewest(true)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILTER_REQUEST_CODE && resultCode == RESULT_OK) {
            filterMap = data?.extras?.get(FILTER_MAP) as HashMap<String, String>?
                    ?: hashMapOf(SORT_KEY to SortValues.REGISTRATION_DATE.name)
            rvAdapter.updateUsersList(filterMap, membersViewModel.userList)
        }
    }

    enum class SortValues {
        NAMEAZ,
        NAMEZA,
        REGISTRATION_DATE
    }

    private fun fetchNewest(isRefresh: Boolean) {
        srlMembers.isRefreshing = isRefresh
        membersViewModel.getUsers(isRefresh)
    }
}
