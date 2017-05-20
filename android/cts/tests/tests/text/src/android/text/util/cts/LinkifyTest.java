/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.text.util.cts;


import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.text.util.Linkify.MatchFilter;
import android.text.util.Linkify.TransformFilter;
import android.util.Patterns;
import android.widget.TextView;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test {@link Linkify}.
 */
public class LinkifyTest extends AndroidTestCase {
    private static final Pattern LINKIFY_TEST_PATTERN = Pattern.compile(
            "(test:)?[a-zA-Z0-9]+(\\.pattern)?");

    private MatchFilter mMatchFilterStartWithDot = new MatchFilter() {
        public final boolean acceptMatch(final CharSequence s, final int start, final int end) {
            if (start == 0) {
                return true;
            }

            if (s.charAt(start - 1) == '.') {
                return false;
            }

            return true;
        }
    };

    private TransformFilter mTransformFilterUpperChar = new TransformFilter() {
        public final String transformUrl(final Matcher match, String url) {
            StringBuilder buffer = new StringBuilder();
            String matchingRegion = match.group();

            for (int i = 0, size = matchingRegion.length(); i < size; i++) {
                char character = matchingRegion.charAt(i);

                if (character == '.' || Character.isLowerCase(character)
                        || Character.isDigit(character)) {
                    buffer.append(character);
                }
            }
            return buffer.toString();
        }
    };

    public void testConstructor() {
        new Linkify();
    }

    public void testAddLinks1() {
        // Verify URLs including the ones that have new gTLDs, and the
        // ones that look like gTLDs (and so are accepted by linkify)
        // and the ones that should not be linkified due to non-compliant
        // gTLDs
        final String longGTLD =
                "abcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabcdefghijabc";
        SpannableString spannable = new SpannableString("name@gmail.com, "
                + "www.google.com, http://www.google.com/language_tools?hl=en, "
                + "a.bd, "   // a URL with accepted TLD so should be linkified
                + "d.e, f.1, g.12, "  // not valid, so should not be linkified
                + "http://h." + longGTLD + " "  // valid, should be linkified
                + "j." + longGTLD + "a"); // not a valid URL (gtld too long), no linkify

        assertTrue(Linkify.addLinks(spannable, Linkify.WEB_URLS));
        URLSpan[] spans = spannable.getSpans(0, spannable.length(), URLSpan.class);
        assertEquals(4, spans.length);
        assertEquals("http://www.google.com", spans[0].getURL());
        assertEquals("http://www.google.com/language_tools?hl=en", spans[1].getURL());
        assertEquals("http://a.bd", spans[2].getURL());
        assertEquals("http://h." + longGTLD, spans[3].getURL());

        assertTrue(Linkify.addLinks(spannable, Linkify.EMAIL_ADDRESSES));
        spans = spannable.getSpans(0, spannable.length(), URLSpan.class);
        assertEquals(1, spans.length);
        assertEquals("mailto:name@gmail.com", spans[0].getURL());

        try {
            Linkify.addLinks((Spannable) null, Linkify.WEB_URLS);
            fail("Should throw NullPointerException!");
        } catch (NullPointerException e) {
            // expect
        }

        assertFalse(Linkify.addLinks((Spannable) null, 0));
    }

    public void testAddLinks2() {
        String text = "www.google.com, name@gmail.com";
        TextView tv = new TextView(mContext);
        tv.setText(text);

        assertTrue(Linkify.addLinks(tv, Linkify.WEB_URLS));
        URLSpan[] spans = ((Spannable)tv.getText()).getSpans(0, text.length(), URLSpan.class);
        assertEquals(1, spans.length);
        assertEquals("http://www.google.com", spans[0].getURL());

        SpannableString spannable = SpannableString.valueOf(text);
        tv.setText(spannable);
        assertTrue(Linkify.addLinks(tv, Linkify.EMAIL_ADDRESSES));
        spans = ((Spannable)tv.getText()).getSpans(0, text.length(), URLSpan.class);
        assertEquals(1, spans.length);
        assertEquals("mailto:name@gmail.com", spans[0].getURL());

        try {
            Linkify.addLinks((TextView)null, Linkify.WEB_URLS);
            fail("Should throw NullPointerException!");
        } catch (NullPointerException e) {
            // expect
        }

        assertFalse(Linkify.addLinks((TextView)null, 0));
    }

    public void testAddLinks3() {
        String text = "Alan, Charlie";
        TextView tv = new TextView(mContext);
        tv.setText(text);

        Linkify.addLinks(tv, LINKIFY_TEST_PATTERN, "Test:");
        URLSpan[] spans = ((Spannable) tv.getText()).getSpans(0, text.length(), URLSpan.class);
        assertEquals(2, spans.length);
        assertEquals("test:Alan", spans[0].getURL());
        assertEquals("test:Charlie", spans[1].getURL());

        text = "google.pattern, test:AZ0101.pattern";
        tv.setText(text);
        Linkify.addLinks(tv, LINKIFY_TEST_PATTERN, "Test:");
        spans = ((Spannable) tv.getText()).getSpans(0, text.length(), URLSpan.class);
        assertEquals(2, spans.length);
        assertEquals("test:google.pattern", spans[0].getURL());
        assertEquals("test:AZ0101.pattern", spans[1].getURL());

        try {
            Linkify.addLinks((TextView) null, LINKIFY_TEST_PATTERN, "Test:");
            fail("Should throw NullPointerException!");
        } catch (NullPointerException e) {
            // expect
        }

        try {
            Linkify.addLinks(tv, null, "Test:");
            fail("Should throw NullPointerException!");
        } catch (NullPointerException e) {
            // expect
        }

        tv = new TextView(mContext);
        tv.setText(text);
        Linkify.addLinks(tv, LINKIFY_TEST_PATTERN, null);
        spans = ((Spannable) tv.getText()).getSpans(0, text.length(), URLSpan.class);
        assertEquals(2, spans.length);
        assertEquals("google.pattern", spans[0].getURL());
        assertEquals("test:AZ0101.pattern", spans[1].getURL());
    }

    public void testAddLinks4() {
        TextView tv = new TextView(mContext);

        String text =  "FilterUpperCase.pattern, 12.345.pattern";
        tv.setText(text);
        Linkify.addLinks(tv, LINKIFY_TEST_PATTERN, "Test:",
                mMatchFilterStartWithDot, mTransformFilterUpperChar);
        URLSpan[] spans = ((Spannable) tv.getText()).getSpans(0, text.length(), URLSpan.class);
        assertEquals(2, spans.length);
        assertEquals("test:ilterpperase.pattern", spans[0].getURL());
        assertEquals("test:12", spans[1].getURL());

        try {
            Linkify.addLinks((TextView) null, LINKIFY_TEST_PATTERN, "Test:",
                    mMatchFilterStartWithDot, mTransformFilterUpperChar);
            fail("Should throw NullPointerException!");
        } catch (NullPointerException e) {
            // expect
        }

        try {
            Linkify.addLinks(tv, null, "Test:",
                    mMatchFilterStartWithDot, mTransformFilterUpperChar);
            fail("Should throw NullPointerException!");
        } catch (NullPointerException e) {
            // expect
        }

        tv.setText(text);
        Linkify.addLinks(tv, LINKIFY_TEST_PATTERN, null,
                mMatchFilterStartWithDot, mTransformFilterUpperChar);
        spans = ((Spannable) tv.getText()).getSpans(0, text.length(), URLSpan.class);
        assertEquals(2, spans.length);
        assertEquals("ilterpperase.pattern", spans[0].getURL());
        assertEquals("12", spans[1].getURL());

        tv.setText(text);
        Linkify.addLinks(tv, LINKIFY_TEST_PATTERN, "Test:", null, mTransformFilterUpperChar);
        spans = ((Spannable) tv.getText()).getSpans(0, text.length(), URLSpan.class);
        assertEquals(3, spans.length);
        assertEquals("test:ilterpperase.pattern", spans[0].getURL());
        assertEquals("test:12", spans[1].getURL());
        assertEquals("test:345.pattern", spans[2].getURL());

        tv.setText(text);
        Linkify.addLinks(tv, LINKIFY_TEST_PATTERN, "Test:", mMatchFilterStartWithDot, null);
        spans = ((Spannable) tv.getText()).getSpans(0, text.length(), URLSpan.class);
        assertEquals(2, spans.length);
        assertEquals("test:FilterUpperCase.pattern", spans[0].getURL());
        assertEquals("test:12", spans[1].getURL());
    }

    public void testAddLinks5() {
        String text = "google.pattern, test:AZ0101.pattern";

        SpannableString spannable = new SpannableString(text);
        Linkify.addLinks(spannable, LINKIFY_TEST_PATTERN, "Test:");
        URLSpan[] spans = (spannable.getSpans(0, spannable.length(), URLSpan.class));
        assertEquals(2, spans.length);
        assertEquals("test:google.pattern", spans[0].getURL());
        assertEquals("test:AZ0101.pattern", spans[1].getURL());

        try {
            Linkify.addLinks((Spannable)null, LINKIFY_TEST_PATTERN, "Test:");
            fail("Should throw NullPointerException!");
        } catch (NullPointerException e) {
        }

        try {
            Linkify.addLinks(spannable, null, "Test:");
            fail("Should throw NullPointerException!");
        } catch (NullPointerException e) {
        }

        spannable = new SpannableString(text);
        Linkify.addLinks(spannable, LINKIFY_TEST_PATTERN, null);
        spans = (spannable.getSpans(0, spannable.length(), URLSpan.class));
        assertEquals(2, spans.length);
        assertEquals("google.pattern", spans[0].getURL());
        assertEquals("test:AZ0101.pattern", spans[1].getURL());
    }

    public void testAddLinks6() {
        String text = "FilterUpperCase.pattern, 12.345.pattern";

        SpannableString spannable = new SpannableString(text);
        Linkify.addLinks(spannable, LINKIFY_TEST_PATTERN, "Test:",
                mMatchFilterStartWithDot, mTransformFilterUpperChar);
        URLSpan[] spans = (spannable.getSpans(0, spannable.length(), URLSpan.class));
        assertEquals(2, spans.length);
        assertEquals("test:ilterpperase.pattern", spans[0].getURL());
        assertEquals("test:12", spans[1].getURL());

        try {
            Linkify.addLinks((Spannable)null, LINKIFY_TEST_PATTERN, "Test:",
                    mMatchFilterStartWithDot, mTransformFilterUpperChar);
            fail("Should throw NullPointerException!");
        } catch (NullPointerException e) {
            // expect
        }

        try {
            Linkify.addLinks(spannable, null, "Test:", mMatchFilterStartWithDot,
                    mTransformFilterUpperChar);
            fail("Should throw NullPointerException!");
        } catch (NullPointerException e) {
            // expect
        }

        spannable = new SpannableString(text);
        Linkify.addLinks(spannable, LINKIFY_TEST_PATTERN, null, mMatchFilterStartWithDot,
                mTransformFilterUpperChar);
        spans = (spannable.getSpans(0, spannable.length(), URLSpan.class));
        assertEquals(2, spans.length);
        assertEquals("ilterpperase.pattern", spans[0].getURL());
        assertEquals("12", spans[1].getURL());

        spannable = new SpannableString(text);
        Linkify.addLinks(spannable, LINKIFY_TEST_PATTERN, "Test:", null, mTransformFilterUpperChar);
        spans = (spannable.getSpans(0, spannable.length(), URLSpan.class));
        assertEquals(3, spans.length);
        assertEquals("test:ilterpperase.pattern", spans[0].getURL());
        assertEquals("test:12", spans[1].getURL());
        assertEquals("test:345.pattern", spans[2].getURL());

        spannable = new SpannableString(text);
        Linkify.addLinks(spannable, LINKIFY_TEST_PATTERN, "Test:", mMatchFilterStartWithDot, null);
        spans = (spannable.getSpans(0, spannable.length(), URLSpan.class));
        assertEquals(2, spans.length);
        assertEquals("test:FilterUpperCase.pattern", spans[0].getURL());
        assertEquals("test:12", spans[1].getURL());
    }

    public void testAddLinks7() {
        String numbersInvalid = "123456789 not a phone number";
        String numbersUKLocal = "tel:(0812)1234560 (0812)1234561";
        String numbersUSLocal = "tel:(812)1234562 (812)123.4563 "
                + " tel:(800)5551210 (800)555-1211 555-1212";
        String numbersIntl = "tel:+4408121234564 +44-0812-123-4565"
                + " tel:+18005551213 +1-800-555-1214";
        SpannableString spannable = new SpannableString(
                numbersInvalid
                + " " + numbersUKLocal
                + " " + numbersUSLocal
                + " " + numbersIntl);

        // phonenumber linkify is locale-dependent
        if (Locale.US.equals(Locale.getDefault())) {
            assertTrue(Linkify.addLinks(spannable, Linkify.PHONE_NUMBERS));
            URLSpan[] spans = spannable.getSpans(0, spannable.length(), URLSpan.class);
            assertEquals(9, spans.length);
            assertEquals("tel:8121234562", spans[0].getURL());
            assertEquals("tel:8121234563", spans[1].getURL());
            assertEquals("tel:8005551210", spans[2].getURL());
            assertEquals("tel:8005551211", spans[3].getURL());
            assertEquals("tel:5551212", spans[4].getURL());
            assertEquals("tel:+4408121234564", spans[5].getURL());
            assertEquals("tel:+4408121234565", spans[6].getURL());
            assertEquals("tel:+18005551213", spans[7].getURL());
            assertEquals("tel:+18005551214", spans[8].getURL());
        }

        try {
            Linkify.addLinks((Spannable) null, Linkify.WEB_URLS);
            fail("Should throw NullPointerException!");
        } catch (NullPointerException e) {
            // expect
        }

        assertFalse(Linkify.addLinks((Spannable) null, 0));
    }

    @SmallTest
    public void testAddLinks_addsLinksWhenDefaultSchemeIsNull() {
        Spannable spannable = new SpannableString("any https://android.com any android.com any");
        Linkify.addLinks(spannable, Patterns.AUTOLINK_WEB_URL, null, null, null);

        URLSpan[] spans = spannable.getSpans(0, spannable.length(), URLSpan.class);
        assertEquals("android.com and https://android.com should be linkified", 2, spans.length);
        assertEquals("https://android.com", spans[0].getURL());
        assertEquals("android.com", spans[1].getURL());
    }

    @SmallTest
    public void testAddLinks_addsLinksWhenSchemesArrayIsNull() {
        Spannable spannable = new SpannableString("any https://android.com any android.com any");
        Linkify.addLinks(spannable, Patterns.AUTOLINK_WEB_URL, "http://", null, null);

        URLSpan[] spans = spannable.getSpans(0, spannable.length(), URLSpan.class);
        assertEquals("android.com and https://android.com should be linkified", 2, spans.length);
        // expected behavior, passing null schemes array means: prepend defaultScheme to all links.
        assertEquals("http://https://android.com", spans[0].getURL());
        assertEquals("http://android.com", spans[1].getURL());
    }

    @SmallTest
    public void testAddLinks_prependsDefaultSchemeToBeginingOfLink() {
        Spannable spannable = new SpannableString("any android.com any");
        Linkify.addLinks(spannable, Patterns.AUTOLINK_WEB_URL, "http://",
                new String[] { "http://", "https://"}, null, null);

        URLSpan[] spans = spannable.getSpans(0, spannable.length(), URLSpan.class);
        assertEquals("android.com should be linkified", 1, spans.length);
        assertEquals("http://android.com", spans[0].getURL());
    }

    @SmallTest
    public void testAddLinks_doesNotPrependSchemeIfSchemeExists() {
        Spannable spannable = new SpannableString("any https://android.com any");
        Linkify.addLinks(spannable, Patterns.AUTOLINK_WEB_URL, "http://",
                new String[] { "http://", "https://"}, null, null);

        URLSpan[] spans = spannable.getSpans(0, spannable.length(), URLSpan.class);
        assertEquals("android.com should be linkified", 1, spans.length);
        assertEquals("https://android.com", spans[0].getURL());
    }

    // Add links with scheme (array)

    @SmallTest
    public void testAddLinks_withTextView_addsLinksWhenDefaultSchemeIsNull() {
        Pattern pattern = Pattern.compile("\\b((http|https)://)?android\\.com+\\b");
        TextView textView = new TextView(mContext);
        textView.setText("any https://android.com any android.com any");

        Linkify.addLinks(textView, pattern, null, null, null);

        URLSpan[] spans = textView.getUrls();
        assertEquals("android.com and https://android.com should be linkified", 2, spans.length);
        assertEquals("https://android.com", spans[0].getURL());
        assertEquals("android.com", spans[1].getURL());
    }

    @SmallTest
    public void testAddLinks_withTextView_addsLinksWhenSchemesArrayIsNull() {
        Pattern pattern = Pattern.compile("\\b((http|https)://)?android\\.com+\\b");
        TextView textView = new TextView(mContext);
        textView.setText("any https://android.com any android.com any");

        Linkify.addLinks(textView, pattern, "http://", null, null);

        URLSpan[] spans = textView.getUrls();
        assertEquals("android.com and https://android.com should be linkified", 2, spans.length);
        // expected behavior, passing null schemes array means: prepend defaultScheme to all links.
        assertEquals("http://https://android.com", spans[0].getURL());
        assertEquals("http://android.com", spans[1].getURL());
    }

    @SmallTest
    public void testAddLinks_withTextView_prependsDefaultSchemeToBeginingOfLink() {
        Pattern pattern = Pattern.compile("\\b((http|https)://)?android\\.com+\\b");
        TextView textView = new TextView(mContext);
        textView.setText("any android.com any");

        Linkify.addLinks(textView, pattern, "http://", new String[] { "http://", "https://"},
                null, null);

        URLSpan[] spans = textView.getUrls();
        assertEquals("android.com should be linkified", 1, spans.length);
        assertEquals("http://android.com", spans[0].getURL());
    }

    @SmallTest
    public void testAddLinks_withTextView_doesNotPrependSchemeIfSchemeExists() {
        Pattern pattern = Pattern.compile("\\b((http|https)://)?android\\.com+\\b");
        TextView textView = new TextView(mContext);
        textView.setText("any https://android.com any");

        Linkify.addLinks(textView, pattern, "http://", new String[] { "http://", "https://"},
                null, null);

        URLSpan[] spans = textView.getUrls();
        assertEquals("android.com should be linkified", 1, spans.length);
        assertEquals("https://android.com", spans[0].getURL());
    }

    // WEB_URLS Related Tests

    @SmallTest
    public void testAddLinks_doesNotAddLinksForUrlWithoutProtocolAndWithoutKnownTld()
            throws Exception {
        Spannable spannable = new SpannableString("hey man.its me");
        boolean linksAdded = Linkify.addLinks(spannable, Linkify.ALL);
        assertFalse("Should not add link with unknown TLD", linksAdded);
    }

    @SmallTest
    public void testAddLinks_shouldNotAddEmailAddressAsUrl() throws Exception {
        String url = "name@gmail.com";
        assertAddLinksWithWebUrlFails("Should not recognize email address as URL", url);
    }

    public void testAddLinks_acceptsUrlsWithCommasInRequestParameterValues() throws Exception {
        String url = "https://android.com/path?ll=37.4221,-122.0836&z=17&pll=37.4221,-122.0836";
        assertAddLinksWithWebUrlSucceeds("Should accept commas", url);
    }

    @SmallTest
    public void testAddLinks_addsLinksForUrlWithProtocolWithoutTld() throws Exception {
        String url = "http://android/#notld///a/n/d/r/o/i/d&p1=1&p2=2";
        assertAddLinksWithWebUrlSucceeds("Should accept URL starting with protocol but does not" +
                " have TLD", url);
    }

    @SmallTest
    public void testAddLinks_matchesProtocolCaseInsensitive() throws Exception {
        String url = "hTtP://android.com";
        assertAddLinksWithWebUrlSucceeds("Protocol matching should be case insensitive", url);
    }

    @SmallTest
    public void testAddLinks_matchesValidUrlWithSchemeAndHostname() throws Exception {
        String url = "http://www.android.com";
        assertAddLinksWithWebUrlSucceeds("Should match valid URL with scheme and hostname", url);
    }

    @SmallTest
    public void testAddLinks_matchesValidUrlWithSchemeHostnameAndNewTld() throws Exception {
        String url = "http://www.android.me";
        assertAddLinksWithWebUrlSucceeds("Should match valid URL with scheme hostname and new TLD",
                url);
    }

    @SmallTest
    public void testAddLinks_matchesValidUrlWithHostnameAndNewTld() throws Exception {
        String url = "android.camera";
        assertAddLinksWithWebUrlSucceeds("Should match valid URL with hostname and new TLD", url);
    }

    @SmallTest
    public void testAddLinks_matchesPunycodeUrl() throws Exception {
        String url = "http://xn--fsqu00a.xn--unup4y";
        assertAddLinksWithWebUrlSucceeds("Should match Punycode URL", url);
    }

    @SmallTest
    public void testAddLinks_matchesPunycodeUrlWithoutProtocol() throws Exception {
        String url = "xn--fsqu00a.xn--unup4y";
        assertAddLinksWithWebUrlSucceeds("Should match Punycode URL without protocol", url);
    }

    @SmallTest
    public void testAddLinks_doesNotMatchPunycodeTldThatStartsWithDash() throws Exception {
        String url = "xn--fsqu00a.-xn--unup4y";
        assertAddLinksWithWebUrlFails("Should not match Punycode TLD that starts with dash", url);
    }

    @SmallTest
    public void testAddLinks_partiallyMatchesPunycodeTldThatEndsWithDash() throws Exception {
        String url = "http://xn--fsqu00a.xn--unup4y-";
        assertAddLinksWithWebUrlPartiallyMatches("Should partially match Punycode TLD that ends " +
                "with dash", "http://xn--fsqu00a.xn--unup4y", url);
    }

    @SmallTest
    public void testAddLinks_matchesUrlWithUnicodeDomainName() throws Exception {
        String url = "http://\uD604\uAE08\uC601\uC218\uC99D.kr";
        assertAddLinksWithWebUrlSucceeds("Should match URL with Unicode domain name", url);
    }

    @SmallTest
    public void testAddLinks_matchesUrlWithUnicodeDomainNameWithoutProtocol() throws Exception {
        String url = "\uD604\uAE08\uC601\uC218\uC99D.kr";
        assertAddLinksWithWebUrlSucceeds("Should match URL without protocol and with Unicode " +
                "domain name", url);
    }

    @SmallTest
    public void testAddLinks_matchesUrlWithUnicodeDomainNameAndTld() throws Exception {
        String url = "\uB3C4\uBA54\uC778.\uD55C\uAD6D";
        assertAddLinksWithWebUrlSucceeds("Should match URL with Unicode domain name and TLD", url);
    }

    @SmallTest
    public void testAddLinks_matchesUrlWithUnicodePath() throws Exception {
        String url = "http://android.com/\u2019/a";
        assertAddLinksWithWebUrlSucceeds("Should match URL with Unicode path", url);
    }

    @SmallTest
    public void testAddLinks_matchesValidUrlWithPort() throws Exception {
        String url = "http://www.example.com:8080";
        assertAddLinksWithWebUrlSucceeds("Should match URL with port", url);
    }

    @SmallTest
    public void testAddLinks_matchesUrlWithPortAndQuery() throws Exception {
        String url = "http://www.example.com:8080/?foo=bar";
        assertAddLinksWithWebUrlSucceeds("Should match URL with port and query", url);
    }

    @SmallTest
    public void testAddLinks_matchesUrlWithTilde() throws Exception {
        String url = "http://www.example.com:8080/~user/?foo=bar";
        assertAddLinksWithWebUrlSucceeds("Should match URL with tilde", url);
    }

    @SmallTest
    public void testAddLinks_matchesUrlStartingWithHttpAndDoesNotHaveTld() throws Exception {
        String url = "http://android/#notld///a/n/d/r/o/i/d&p1=1&p2=2";
        assertAddLinksWithWebUrlSucceeds("Should match URL without a TLD and starting with http",
                url);
    }

    @SmallTest
    public void testAddLinks_doesNotMatchUrlsWithoutProtocolAndWithUnknownTld() throws Exception {
        String url = "thank.you";
        assertAddLinksWithWebUrlFails("Should not match URL that does not start with a protocol " +
                "and does not contain a known TLD", url);
    }

    @SmallTest
    public void testAddLinks_partiallyMatchesUrlWithInvalidRequestParameter() throws Exception {
        String url = "http://android.com?p=value";
        assertAddLinksWithWebUrlPartiallyMatches("Should partially match URL with invalid " +
                "request parameter", "http://android.com", url);
    }

    @SmallTest
    public void testAddLinks_matchesValidUrlWithEmoji() throws Exception {
        String url = "Thank\u263A.com";
        assertAddLinksWithWebUrlSucceeds("Should match URL with emoji", url);
    }

    @SmallTest
    public void testAddLinks_doesNotMatchUrlsWithEmojiWithoutProtocolAndWithoutKnownTld()
            throws Exception {
        String url = "Thank\u263A.you";
        assertAddLinksWithWebUrlFails("Should not match URLs containing emoji and with unknown " +
                "TLD", url);
    }

    @SmallTest
    public void testAddLinks_matchesDomainNameWithSurrogatePairs() throws Exception {
        String url = "android\uD83C\uDF38.com";
        assertAddLinksWithWebUrlSucceeds("Should match domain name with Unicode surrogate pairs",
                url);
    }

    @SmallTest
    public void testAddLinks_matchesTldWithSurrogatePairs() throws Exception {
        String url = "http://android.\uD83C\uDF38com";
        assertAddLinksWithWebUrlSucceeds("Should match TLD with Unicode surrogate pairs", url);
    }

    @SmallTest
    public void testAddLinks_doesNotMatchUrlWithExcludedSurrogate() throws Exception {
        String url = "android\uD83F\uDFFE.com";
        assertAddLinksWithWebUrlFails("Should not match URL with excluded Unicode surrogate" +
                " pair",  url);
    }

    @SmallTest
    public void testAddLinks_matchesPathWithSurrogatePairs() throws Exception {
        String url = "http://android.com/path-with-\uD83C\uDF38?v=\uD83C\uDF38f";
        assertAddLinksWithWebUrlSucceeds("Should match path and query with Unicode surrogate pairs",
                url);
    }

    @SmallTest
    public void testAddLinks__doesNotMatchUnicodeSpaces() throws Exception {
        String part1 = "http://and";
        String part2 = "roid.com";
        String[] emptySpaces = new String[]{
                "\u00A0", // no-break space
                "\u2000", // en quad
                "\u2001", // em quad
                "\u2002", // en space
                "\u2003", // em space
                "\u2004", // three-per-em space
                "\u2005", // four-per-em space
                "\u2006", // six-per-em space
                "\u2007", // figure space
                "\u2008", // punctuation space
                "\u2009", // thin space
                "\u200A", // hair space
                "\u2028", // line separator
                "\u2029", // paragraph separator
                "\u202F", // narrow no-break space
                "\u3000"  // ideographic space
        };

        for (String emptySpace : emptySpaces) {
            String url = part1 + emptySpace + part2;
            assertAddLinksWithWebUrlPartiallyMatches("Should not include empty space with code: " +
                    emptySpace.codePointAt(0), part1, url);
        }
    }

    // EMAIL_ADDRESSES Related Tests

    public void testAddLinks_email_matchesShortValidEmail() throws Exception {
        String email = "a@a.co";
        assertAddLinksWithEmailSucceeds("Should match email: " + email, email);
    }

    public void testAddLinks_email_matchesRegularEmail() throws Exception {
        String email = "email@android.com";
        assertAddLinksWithEmailSucceeds("Should match email: " + email, email);
    }

    public void testAddLinks_email_matchesEmailWithMultipleSubdomains() throws Exception {
        String email = "email@e.somelongdomainnameforandroid.abc.uk";
        assertAddLinksWithEmailSucceeds("Should match email: " + email, email);
    }

    public void testAddLinks_email_matchesLocalPartWithDot() throws Exception {
        String email = "e.mail@android.com";
        assertAddLinksWithEmailSucceeds("Should match email: " + email, email);
    }

    public void testAddLinks_email_matchesLocalPartWithPlus() throws Exception {
        String email = "e+mail@android.com";
        assertAddLinksWithEmailSucceeds("Should match email: " + email, email);
    }

    public void testAddLinks_email_matchesLocalPartWithUnderscore() throws Exception {
        String email = "e_mail@android.com";
        assertAddLinksWithEmailSucceeds("Should match email: " + email, email);
    }

    public void testAddLinks_email_matchesLocalPartWithDash() throws Exception {
        String email = "e-mail@android.com";
        assertAddLinksWithEmailSucceeds("Should match email: " + email, email);
    }

    public void testAddLinks_email_matchesLocalPartWithApostrophe() throws Exception {
        String email = "e'mail@android.com";
        assertAddLinksWithEmailSucceeds("Should match email: " + email, email);
    }

    public void testAddLinks_email_matchesLocalPartWithDigits() throws Exception {
        String email = "123@android.com";
        assertAddLinksWithEmailSucceeds("Should match email: " + email, email);
    }

    public void testAddLinks_email_matchesUnicodeLocalPart() throws Exception {
        String email = "\uD604\uAE08\uC601\uC218\uC99D@android.kr";
        assertAddLinksWithEmailSucceeds("Should match email: " + email, email);
    }

    public void testAddLinks_email_matchesLocalPartWithEmoji() throws Exception {
        String email = "smiley\u263A@android.com";
        assertAddLinksWithEmailSucceeds("Should match email: " + email, email);
    }

    public void testAddLinks_email_matchesLocalPartWithSurrogatePairs()
            throws Exception {
        String email = "a\uD83C\uDF38a@android.com";
        assertAddLinksWithEmailSucceeds("Should match email: " + email, email);
    }

    public void testAddLinks_email_matchesDomainWithDash() throws Exception {
        String email = "email@an-droid.com";
        assertAddLinksWithEmailSucceeds("Should match email: " + email, email);
    }

    public void testAddLinks_email_matchesUnicodeDomain() throws Exception {
        String email = "email@\uD604\uAE08\uC601\uC218\uC99D.kr";
        assertAddLinksWithEmailSucceeds("Should match email: " + email, email);
    }

    public void testAddLinks_email_matchesUnicodeLocalPartAndDomain()
            throws Exception {
        String email = "\uD604\uAE08\uC601\uC218\uC99D@\uD604\uAE08\uC601\uC218\uC99D.kr";
        assertAddLinksWithEmailSucceeds("Should match email: " + email, email);
    }

    public void testAddLinks_email_matchesDomainWithEmoji() throws Exception {
        String email = "smiley@\u263Aandroid.com";
        assertAddLinksWithEmailSucceeds("Should match email: " + email, email);
    }

    public void testAddLinks_email_matchesDomainWithSurrogatePairs()
            throws Exception {
        String email = "email@\uD83C\uDF38android.com";
        assertAddLinksWithEmailSucceeds("Should match email: " + email, email);
    }

    public void testAddLinks_email_matchesLocalPartAndDomainWithSurrogatePairs()
            throws Exception {
        String email = "a\uD83C\uDF38a@\uD83C\uDF38android.com";
        assertAddLinksWithEmailSucceeds("Should match email: " + email, email);
    }

    public void testAddLinks_partiallyMatchesEmailEndingWithDot() throws Exception {
        String email = "email@android.co.uk.";
        assertAddLinksWithEmailPartiallyMatches("Should partially match email ending with dot",
                "mailto:email@android.co.uk", email);
    }

    public void testAddLinks_email_partiallyMatchesLocalPartStartingWithDot()
            throws Exception {
        String email = ".email@android.com";
        assertAddLinksWithEmailPartiallyMatches("Should partially match email starting " +
                "with dot", "mailto:email@android.com", email);
    }

    public void testAddLinks_email_doesNotMatchStringWithoutAtSign() throws Exception {
        String email = "android.com";
        assertAddLinksWithEmailFails("Should not match email: " + email, email);
    }

    public void testAddLinks_email_doesNotMatchPlainString() throws Exception {
        String email = "email";
        assertAddLinksWithEmailFails("Should not match email: " + email, email);
    }

    public void testAddLinks_email_doesNotMatchEmailWithoutTld() throws Exception {
        String email = "email@android";
        assertAddLinksWithEmailFails("Should not match email: " + email, email);
    }

    public void testAddLinks_email_doesNotMatchLocalPartEndingWithDot()
            throws Exception {
        String email = "email.@android.com";
        assertAddLinksWithEmailFails("Should not match email: " + email, email);
    }

    public void testAddLinks_email_doesNotMatchDomainStartingWithDash()
            throws Exception {
        String email = "email@-android.com";
        assertAddLinksWithEmailFails("Should not match email: " + email, email);
    }

    public void testAddLinks_email_doesNotMatchDomainWithConsecutiveDots()
            throws Exception {
        String email = "email@android..com";
        assertAddLinksWithEmailFails("Should not match email: " + email, email);
    }

    public void testAddLinks_email_doesNotMatchEmailWithIp() throws Exception {
        String email = "email@127.0.0.1";
        assertAddLinksWithEmailFails("Should not match email: " + email, email);
    }

    public void testAddLinks_email_doesNotMatchEmailWithInvalidTld()
            throws Exception {
        String email = "email@android.c";
        assertAddLinksWithEmailFails("Should not match email: " + email, email);
    }

    public void testAddLinks_email_matchesLocalPartUpTo64Chars() throws Exception {
        String localPart = "";
        for (int i = 0; i < 64; i++) {
            localPart += "a";
        }
        String email = localPart + "@android.com";
        assertAddLinksWithEmailSucceeds("Should match email local part of length: " +
                localPart.length(), email);

        email = localPart + "a@android.com";
        assertAddLinksWithEmailFails("Should not match email local part of length:" +
                localPart.length(), email);
    }

    public void testAddLinks_email_matchesSubdomainUpTo63Chars() throws Exception {
        String subdomain = "";
        for (int i = 0; i < 63; i++) {
            subdomain += "a";
        }
        String email = "email@" + subdomain + ".com";

        assertAddLinksWithEmailSucceeds("Should match email subdomain of length: " +
                subdomain.length(), email);

        subdomain += "a";
        email = "email@" + subdomain + ".com";

        assertAddLinksWithEmailFails("Should not match email subdomain of length:" +
                subdomain.length(), email);
    }

    public void testAddLinks_email_matchesDomainUpTo255Chars() throws Exception {
        String domain = "";
        while (domain.length() <= 250) {
            domain += "d.";
        }
        domain += "com";
        assertEquals(255, domain.length());
        String email = "a@" + domain;
        assertAddLinksWithEmailSucceeds("Should match email domain of length: " +
                domain.length(), email);

        email = email + "m";
        assertAddLinksWithEmailFails("Should not match email domain of length:" +
                domain.length(), email);
    }

    // Utility functions
    private static void assertAddLinksWithWebUrlSucceeds(String msg, String url) {
        assertAddLinksSucceeds(msg, url, Linkify.WEB_URLS);
    }

    private static void assertAddLinksWithWebUrlFails(String msg, String url) {
        assertAddLinksFails(msg, url, Linkify.WEB_URLS);
    }

    private static void assertAddLinksWithWebUrlPartiallyMatches(String msg, String expected,
            String url) {
        assertAddLinksPartiallyMatches(msg, expected, url, Linkify.WEB_URLS);
    }

    private static void assertAddLinksWithEmailSucceeds(String msg, String url) {
        assertAddLinksSucceeds(msg, url, Linkify.EMAIL_ADDRESSES);
    }

    private static void assertAddLinksWithEmailFails(String msg, String url) {
        assertAddLinksFails(msg, url, Linkify.EMAIL_ADDRESSES);
    }

    private static void assertAddLinksWithEmailPartiallyMatches(String msg, String expected,
            String url) {
        assertAddLinksPartiallyMatches(msg, expected, url, Linkify.EMAIL_ADDRESSES);
    }

    private static void assertAddLinksSucceeds(String msg, String string, int type) {
        String str = "start " + string + " end";
        Spannable spannable = new SpannableString(str);

        boolean linksAdded = Linkify.addLinks(spannable, type);
        URLSpan[] spans = spannable.getSpans(0, str.length(), URLSpan.class);

        assertTrue(msg, linksAdded);
        assertEquals("Span should start from the beginning of: " + string,
                "start ".length(), spannable.getSpanStart(spans[0]));
        assertEquals("Span should end at the end of: " + string,
                str.length() - " end".length(), spannable.getSpanEnd(spans[0]));
    }

    private static void assertAddLinksFails(String msg, String string, int type) {
        Spannable spannable = new SpannableString("start " + string + " end");
        boolean linksAdded = Linkify.addLinks(spannable, type);
        assertFalse(msg, linksAdded);
    }

    private static void assertAddLinksPartiallyMatches(String msg, String expected,
                                                       String string, int type) {
        Spannable spannable = new SpannableString("start " + string + " end");
        boolean linksAdded = Linkify.addLinks(spannable, type);
        URLSpan[] spans = spannable.getSpans(0, spannable.length(), URLSpan.class);
        assertTrue(msg, linksAdded);
        assertEquals(msg, expected, spans[0].getURL().toString());
    }
}
