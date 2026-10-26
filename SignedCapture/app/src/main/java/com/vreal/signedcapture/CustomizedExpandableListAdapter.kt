package com.vreal.signedcapture

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseExpandableListAdapter
import android.widget.TextView
import androidx.compose.ui.text.font.Typeface


class CustomizedExpandableListAdapter(
    private val context: Context,
    private val expandableTitleList: List<String>,
    private val expandableDetailList: HashMap<String, List<String>>
) : BaseExpandableListAdapter() {

    override fun getChild(lstPosn: Int, expandedListPosition: Int): Any {
        return expandableDetailList[expandableTitleList[lstPosn]]?.get(expandedListPosition) ?: ""
    }

    override fun getChildId(listPosition: Int, expandedListPosition: Int): Long {
        return expandedListPosition.toLong()
    }

    override fun getChildView(
        lstPosn: Int,
        expandedListPosition: Int,
        isLastChild: Boolean,
        convertView: View?,
        parent: ViewGroup?
    ): View {
        val expandedListText = getChild(lstPosn, expandedListPosition) as String
        val convertView = convertView ?: LayoutInflater.from(context).inflate(R.layout.list_item, parent, false)
        val expandedListTextView: TextView = convertView.findViewById(R.id.expandedListItem)
        expandedListTextView.text = expandedListText
        return convertView
    }

    override fun getChildrenCount(listPosition: Int): Int {
        return expandableDetailList[expandableTitleList[listPosition]]?.size ?: 0
    }

    override fun getGroup(listPosition: Int): Any {
        return expandableTitleList[listPosition]
    }

    override fun getGroupCount(): Int {
        return expandableTitleList.size
    }

    override fun getGroupId(listPosition: Int): Long {
        return listPosition.toLong()
    }

    override fun getGroupView(
        listPosition: Int,
        isExpanded: Boolean,
        convertView: View?,
        parent: ViewGroup?
    ): View {
        val listTitle = getGroup(listPosition) as String
        val convertView = convertView ?: LayoutInflater.from(context).inflate(R.layout.list_group, parent, false)
        val listTitleTextView: TextView = convertView.findViewById(R.id.listTitle)
//        listTitleTextView.setTypeface(null, Typeface.BOLD)
        listTitleTextView.text = "Signatures"
        return convertView
    }

    override fun hasStableIds(): Boolean {
        return false
    }

    override fun isChildSelectable(listPosition: Int, expandedListPosition: Int): Boolean {
        return true
    }
}