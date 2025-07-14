package com.beatquest

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.firebase.database.FirebaseDatabase

class ChallengeDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_CHALLENGER_ID = "challenger_id"

        fun newInstance(challengerId: String): ChallengeDialogFragment {
            val fragment = ChallengeDialogFragment()
            val args = Bundle()
            args.putString(ARG_CHALLENGER_ID, challengerId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.layout_challenge, container, false)

        val challengerId = arguments?.getString(ARG_CHALLENGER_ID) ?: ""

        val title = view.findViewById<TextView>(R.id.dialog_title)
        val message = view.findViewById<TextView>(R.id.dialog_message)
        val btnAccept = view.findViewById<Button>(R.id.btn_accept)
        val btnDecline = view.findViewById<Button>(R.id.btn_decline)

        message.text = "$challengerId has challenged you! Do you accept?"

        btnAccept.setOnClickListener {
            (activity as? MainActivity)?.acceptChallenge(challengerId)
            dismiss()
        }

        btnDecline.setOnClickListener {
            (activity as? MainActivity)?.declineChallenge(challengerId)
            dismiss()
        }

        dialog?.setOnDismissListener {
            (activity as? MainActivity)?.saveDataToFirebase("challenged", false, "challenges/$challengerId")
        }

        return view
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }
}