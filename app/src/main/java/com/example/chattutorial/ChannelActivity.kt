package com.example.chattutorial

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.getstream.sdk.chat.view.common.visible
import com.getstream.sdk.chat.viewmodel.ChannelHeaderViewModel
import com.getstream.sdk.chat.viewmodel.MessageInputViewModel
import com.getstream.sdk.chat.viewmodel.bindView
import com.getstream.sdk.chat.viewmodel.messages.MessageListViewModel
import com.getstream.sdk.chat.viewmodel.messages.bindView
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.events.TypingStartEvent
import io.getstream.chat.android.client.events.TypingStopEvent
import io.getstream.chat.android.client.models.Channel
import kotlinx.android.synthetic.main.activity_channel.*

class ChannelActivity : AppCompatActivity(R.layout.activity_channel) {

    private val cid by lazy {
        intent.getStringExtra(CID_KEY)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        messageListView.setViewHolderFactory(MyMessageViewHolderFactory())
        val messagesViewModel = MessageListViewModel(cid)
            .apply {
                bindView(messageListView, this@ChannelActivity)
                state.observe(
                    this@ChannelActivity,
                    Observer {
                        when (it) {
                            is MessageListViewModel.State.Loading -> progressBar.visible(true)
                            is MessageListViewModel.State.Result -> progressBar.visible(false)
                            is MessageListViewModel.State.NavigateUp -> finish()
                        }
                    }
                )
            }

        val channelController = ChatClient.instance().channel(cid)
        val currentlyTyping = MutableLiveData<List<String>>(ArrayList())

        channelController.events().subscribe {
            val name = it.user!!.extraData["name"]!! as String
            val typing = currentlyTyping.value ?: listOf()
            val typingCopy: MutableList<String> = typing.toMutableList()
            when (it) {
                is TypingStartEvent -> {
                    if (typingCopy.contains(name).not()) {
                        typingCopy.add(name)
                    }
                    currentlyTyping.postValue(typingCopy)
                }
                is TypingStopEvent -> {
                    typingCopy.remove(name)
                    currentlyTyping.postValue(typingCopy)
                }
            }
        }

        val typingObserver = Observer<List<String>> { users ->
            var typing = "nobody is typing"
            if (users.isNotEmpty()) {
                typing = "typing: " + users.joinToString(", ")
            }
            channelHeaderView.text = typing
        }
        currentlyTyping.observe(this, typingObserver)

        MessageInputViewModel(cid).apply {
            bindView(messageInputView, this@ChannelActivity)
            messagesViewModel.mode.observe(
                this@ChannelActivity,
                Observer {
                    when (it) {
                        is MessageListViewModel.Mode.Thread -> setActiveThread(it.parentMessage)
                        is MessageListViewModel.Mode.Normal -> resetThread()
                    }
                }
            )
            messageListView.setOnMessageEditHandler {
                editMessage.postValue(it)
            }
        }
        val backButtonHandler = {
            messagesViewModel.onEvent(MessageListViewModel.Event.BackButtonPressed)
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    backButtonHandler()
                }
            }
        )
    }

    companion object {
        private const val CID_KEY = "key:cid"

        fun newIntent(context: Context, channel: Channel) =
            Intent(context, ChannelActivity::class.java).apply {
                putExtra(CID_KEY, channel.cid)
            }
    }
}
