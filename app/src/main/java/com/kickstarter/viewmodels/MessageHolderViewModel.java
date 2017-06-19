package com.kickstarter.viewmodels;

import android.support.annotation.NonNull;
import android.util.Pair;

import com.kickstarter.libs.ActivityViewModel;
import com.kickstarter.libs.CurrentUserType;
import com.kickstarter.libs.Environment;
import com.kickstarter.libs.utils.BooleanUtils;
import com.kickstarter.libs.utils.PairUtils;
import com.kickstarter.models.Message;
import com.kickstarter.ui.viewholders.MessageViewHolder;

import org.joda.time.DateTime;

import rx.Observable;
import rx.subjects.PublishSubject;

public interface MessageHolderViewModel {

  interface Inputs {
    /** Call to configure the view model with a message. */
    void configureWith(Message message);
  }

  interface Outputs {
    /** Emits a DateTime to be displayed in the center timestamp text view. */
    Observable<DateTime> centerTimestampDateTime();

    /** Emits a boolean to determine whether the message recipient card view should be gone. */
    Observable<Boolean> messageBodyRecipientCardViewIsGone();

    /** Emits the recipient's message body text view text. */
    Observable<String> messageBodyRecipientTextViewText();

    /** Emits a boolean to determine whether the message sender card view should be gone. */
    Observable<Boolean> messageBodySenderCardViewIsGone();

    /** Emits the sender's message body text view text. */
    Observable<String> messageBodySenderTextViewText();

    /** Emits a boolean that determines whether the participant's avatar image should be hidden. */
    Observable<Boolean> participantAvatarImageHidden();

    /** Emits the url for the participant's avatar image. */
    Observable<String> participantAvatarImageUrl();
  }

  final class ViewModel extends ActivityViewModel<MessageViewHolder> implements Inputs, Outputs {
    private final CurrentUserType currentUser;

    public ViewModel(final @NonNull Environment environment) {
      super(environment);

      this.currentUser = environment.currentUser();

      final Observable<Pair<Message, Boolean>> messageAndCurrentUserIsSender = Observable.combineLatest(
        this.message,
        this.currentUser.loggedInUser(),
        Pair::create
      )
        .map(mu -> Pair.create(mu.first, mu.first.sender().id() == mu.second.id()));

      this.centerTimestampDateTime = this.message.map(Message::createdAt);

      this.messageBodyRecipientCardViewIsGone = messageAndCurrentUserIsSender
        .map(PairUtils::second);

      this.messageBodyRecipientTextViewText = messageAndCurrentUserIsSender
        .filter(mb -> !mb.second)
        .map(mb -> mb.first.body());

      this.messageBodySenderCardViewIsGone = this.messageBodyRecipientCardViewIsGone
        .map(BooleanUtils::negate);

      this.messageBodySenderTextViewText = messageAndCurrentUserIsSender
        .filter(mb -> mb.second)
        .map(mb -> mb.first.body());

      this.participantAvatarImageHidden = this.messageBodyRecipientCardViewIsGone;

      this.participantAvatarImageUrl = messageAndCurrentUserIsSender
        .filter(mb -> !mb.second)
        .map(mb -> mb.first.sender().avatar().medium());
    }

    private final PublishSubject<Message> message = PublishSubject.create();

    private final Observable<DateTime> centerTimestampDateTime;
    private final Observable<Boolean> messageBodyRecipientCardViewIsGone;
    private final Observable<String> messageBodyRecipientTextViewText;
    private final Observable<Boolean> messageBodySenderCardViewIsGone;
    private final Observable<String> messageBodySenderTextViewText;
    private final Observable<Boolean> participantAvatarImageHidden;
    private final Observable<String> participantAvatarImageUrl;

    public final Inputs inputs = this;
    public final Outputs outputs = this;

    @Override public void configureWith(final @NonNull Message message) {
      this.message.onNext(message);
    }

    @Override public @NonNull Observable<DateTime> centerTimestampDateTime() {
      return this.centerTimestampDateTime;
    }
    @Override public @NonNull Observable<Boolean> messageBodyRecipientCardViewIsGone() {
      return this.messageBodyRecipientCardViewIsGone;
    }
    @Override public @NonNull Observable<Boolean> messageBodySenderCardViewIsGone() {
      return this.messageBodySenderCardViewIsGone;
    }
    @Override public @NonNull Observable<String> messageBodySenderTextViewText() {
      return this.messageBodySenderTextViewText;
    }
    @Override public @NonNull Observable<String> messageBodyRecipientTextViewText() {
      return this.messageBodyRecipientTextViewText;
    }
    @Override public @NonNull Observable<Boolean> participantAvatarImageHidden() {
      return this.participantAvatarImageHidden;
    }
    @Override public @NonNull Observable<String> participantAvatarImageUrl() {
      return this.participantAvatarImageUrl;
    }
  }
}
