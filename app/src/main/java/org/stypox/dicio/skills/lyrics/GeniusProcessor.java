package org.stypox.dicio.skills.lyrics;

import static org.stypox.dicio.Sentences_en.lyrics;

import org.stypox.dicio.util.ConnectionUtils;
import org.stypox.dicio.util.RegexUtils;
import org.dicio.skill.chain.IntermediateProcessor;
import org.dicio.skill.standard.StandardResult;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.unbescape.javascript.JavaScriptEscape;
import org.unbescape.json.JsonEscape;

import java.util.regex.Pattern;

public class GeniusProcessor extends IntermediateProcessor<StandardResult, LyricsOutput.Data> {

    // replace "songs" with "multi" to get all kinds of results and not just songs
    private static final String GENIUS_SEARCH_URL = "https://genius.com/api/search/songs?q=";
    private static final String GENIUS_LYRICS_URL = "https://genius.com/songs/";
    private static final Pattern LYRICS_PATTERN =
            Pattern.compile("document\\.write\\(JSON\\.parse\\('(.+)'\\)\\)");
    private static final Pattern NEWLINE_PATTERN =
            Pattern.compile("\\s*(\\\\n)?\\s*\\{#%\\)\\s*");


    @Override
    public LyricsOutput.Data process(final StandardResult data) throws Exception {

        final String songName = data.getCapturingGroup(lyrics.song).trim();
        final JSONObject search = ConnectionUtils.getPageJson(
                GENIUS_SEARCH_URL + ConnectionUtils.urlEncode(songName) + "&count=1");
        final JSONArray searchHits =
                search.getJSONObject("response").getJSONArray("sections")
                        .getJSONObject(0).getJSONArray("hits");

        final LyricsOutput.Data result = new LyricsOutput.Data();
        if (searchHits.length() == 0) {
            result.failed = true;
            result.title = songName;
            return result;
        }

        final JSONObject song = searchHits.getJSONObject(0).getJSONObject("result");
        result.title = song.getString("title");
        result.artist = song.getJSONObject("primary_artist").getString("name");


        String lyricsHtml =
                ConnectionUtils.getPage(
                        GENIUS_LYRICS_URL + song.getInt("id") + "/embed.js");
        lyricsHtml = RegexUtils.matchGroup1(LYRICS_PATTERN, lyricsHtml);
        lyricsHtml = JsonEscape.unescapeJson(JavaScriptEscape.unescapeJavaScript(lyricsHtml));

        final Document lyricsDocument = Jsoup.parse(lyricsHtml);
        final Elements elements = lyricsDocument.select("div[class=rg_embed_body]");
        elements.select("br").append("{#%)");
        result.lyrics = RegexUtils.replaceAll(NEWLINE_PATTERN, elements.text(), "\n");

        return result;
    }
}
