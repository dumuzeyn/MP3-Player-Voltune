package com.dumuzeyn.mp3player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PlaybackTransitionPolicyTest {
    @Test
    public void focusGainResumesOnlyWhenPlaybackWasActiveAndUserDidNotPause() {
        PlaybackTransitionPolicy policy = new PlaybackTransitionPolicy();
        assertEquals(PauseReason.AUDIO_FOCUS, policy.onTemporaryAudioFocusLoss(true));
        assertTrue(policy.shouldResumeAfterAudioFocusGain());
        assertFalse(policy.shouldResumeAfterAudioFocusGain());

        policy.onTemporaryAudioFocusLoss(true);
        policy.onUserPause();
        assertFalse(policy.shouldResumeAfterAudioFocusGain());
    }

    @Test
    public void noisyEventNeverSchedulesAutomaticResume() {
        PlaybackTransitionPolicy policy = new PlaybackTransitionPolicy();
        policy.onTemporaryAudioFocusLoss(true);
        assertEquals(PauseReason.AUDIO_BECOMING_NOISY, policy.onAudioBecomingNoisy());
        assertFalse(policy.shouldResumeAfterAudioFocusGain());
    }

    @Test
    public void recoveryIsBoundedByQueueSize() {
        PlaybackTransitionPolicy policy = new PlaybackTransitionPolicy();
        assertTrue(policy.shouldSkipError(1, 3, true));
        assertTrue(policy.shouldSkipError(2, 3, true));
        assertFalse(policy.shouldSkipError(3, 3, true));
        assertEquals(StopReason.ALL_ITEMS_UNAVAILABLE,
                policy.stopReasonForError(3, 3, true));
        assertEquals(StopReason.FATAL_ERROR,
                policy.stopReasonForError(1, 3, false));
    }
}
