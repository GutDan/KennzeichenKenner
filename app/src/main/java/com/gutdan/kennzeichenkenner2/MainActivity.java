package com.gutdan.kennzeichenkenner2;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.ActionBar;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.Constraints;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private LayoutInflater inflater;
    private ClipboardManager clipboard;

    private TextView tv_stadtlandkreis, tv_abgeleitetvon, tv_bundesland;
    private EditText et_maininput;
    private Button but_search, but_maplayers, but_linksquellen;
    private View vw_locationblob;
    private ImageView iv_map;
    private ExtendedFloatingActionButton efab_floating;
    private ScrollView vw_scroll;
    private ViewSwitcher vw_switcher;
    private LinearLayout ll_links, ll_quellen;


    private List<View>  views_quellen;

    int mapWidth, mapHeight, blobWidth, blobHeight;
    float[] bl, tr;

    private class DBData {
        public String ableitung, land, link;
        public float n, e;
        public String[] kreis;

        public ArrayList<String[]> links;
    }

    HashMap<String, DBData> hashmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // get ui elements
        this.et_maininput = (EditText) findViewById(R.id.et_maininput);
        this.tv_stadtlandkreis = (TextView) findViewById(R.id.stadtlandkreis);
        this.tv_abgeleitetvon = (TextView) findViewById(R.id.abgeleitetvon);
        this.tv_bundesland = (TextView) findViewById(R.id.bundesland);
        this.but_search = (Button) findViewById(R.id.search);
        this.vw_locationblob = (View) findViewById(R.id.locationblob);
        this.iv_map = (ImageView) findViewById(R.id.map);
        this.efab_floating = (ExtendedFloatingActionButton) findViewById(R.id.floating);
        this.vw_scroll = (ScrollView) findViewById(R.id.scrollview);
        this.but_linksquellen = (Button) findViewById(R.id.linksquellen);
        this.but_maplayers = (Button) findViewById(R.id.maplayers);
        this.vw_switcher = (ViewSwitcher) findViewById(R.id.viewswitcher);
        this.ll_links = (LinearLayout) findViewById(R.id.ll_links);
        this.ll_quellen = (LinearLayout) findViewById(R.id.ll_quellen);

        this.views_quellen = new ArrayList<>();

        // get inflater
        this.inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        // get clipboard manager
        this.clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);


        //add sources
        //Liste der Kfz-Kennzeichen in Deutschland
        addLink(getString(R.string.listekfz), getString(R.string.listekfz_link), this.ll_quellen, -1);
        addLink(getString(R.string.cc4), getString(R.string.cc4_link), this.ll_quellen, -1);
        addLink(getString(R.string.karte), getString(R.string.karte_link), this.ll_quellen, -1);
        addLink(getString(R.string.cc3), getString(R.string.cc3_link), this.ll_quellen, -1);
        addLink(getString(R.string.dstrip), getString(R.string.dstrip_link), this.ll_quellen, -1);


        // mercator bl and tr corners
        //bl 47.219334 5.529922
        //tr 55.073038 15.651101
        bl = mercatorProjection(47.21f, 5.52f); // any hardcoders?
        tr = mercatorProjection(55.07f, 15.45f);

        // read database into hashmap
        this.hashmap = new HashMap<>();
        try {
            InputStreamReader stream = new InputStreamReader(getAssets().open("kennzeichendb.csv"));
            BufferedReader reader = new BufferedReader(stream);
            String line;
            while ((line = reader.readLine()) != null) {
                String[] cols = line.replace("\n", "").split(";");

                DBData row = new DBData();
                row.ableitung = cols[1];
                row.land = cols[2];
                row.link = cols[3];

                String[] coords = cols[4].substring(1, cols[4].length()-1).split(",");
                row.n = Float.parseFloat(coords[0]);
                row.e = Float.parseFloat(coords[1]);

                row.kreis = cols[5].substring(1, cols[5].length()-1)
                        .replace("'","").split(", ");


                row.links = new ArrayList<>();
                for (String tuple: cols[6].substring(2, cols[6].length()-2).split("\\), \\(")) {
                    row.links.add(tuple.replace("'","").split(", "));
                }

                this.hashmap.put(cols[0], row);
            }
        } catch (IOException e) {
            Log.e("GD", "IOException on kennzeichendb.csv");
        }

        //focus
        this.et_maininput.requestFocus();

        // search button functionality
        this.but_search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSearch();
            }
        });

        // on enter edit text functionality
        this.et_maininput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    return onSearch();
                }
                return false;
            }
        });

        // on click edit text functionality
        this.et_maininput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                et_maininput.setText(""); //clear text
            }
        });

        // on click floating button functionality
        this.efab_floating.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (efab_floating.getText().equals(getString(R.string.arrow_tl_bar))) { //on map screen
                    et_maininput.setText(""); //clear text
                    //open keyboard
                    InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
                    inputMethodManager.showSoftInput(et_maininput, 0);
                    et_maininput.requestFocus();
                    // scroll up
                    vw_scroll.fullScroll(ScrollView.FOCUS_UP);
                } else { // on linksquelle screen
                    vw_switcher.showNext();
                    efab_floating.setText(getString(R.string.arrow_tl_bar));
                }
            }
        });

        // on click button maplayers functionality
        this.but_maplayers.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (iv_map.getTag() == null) {
                    iv_map.setImageDrawable(getDrawable(R.drawable.map));
                    iv_map.setTag("0");
                    return;
                }
                // cycle images
                if (((String) iv_map.getTag()).equals("0")) {
                    iv_map.setImageDrawable(getDrawable(R.drawable.map_pre));
                    iv_map.setTag("1");
                } else if (((String) iv_map.getTag()).equals("1")) {
                    iv_map.setImageDrawable(getDrawable(R.drawable.map));
                    iv_map.setTag("0");
                } else {
                    iv_map.setImageDrawable(getDrawable(R.drawable.map));
                    iv_map.setTag("0");
                }
            }
        });

        //on click button linksquelle functionality
        this.but_linksquellen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                vw_switcher.showNext();
                efab_floating.setText(getString(R.string.arrow_back));
            }
        });
    }

    private boolean onSearch() { // on search button or done
        boolean updated = updateScreen();
        boolean dont_close_soft_keyboard = true;
        if (updated) { // input was found, close keyboard
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) { //instant
                InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
                inputMethodManager.hideSoftInputFromWindow(et_maininput.getWindowToken(), 0);
            } else { //potentially slow animation
                dont_close_soft_keyboard = false;
            }
        } else { // input not found , clear and animate
            et_maininput.setText("");
            int colorFrom = getResources().getColor(R.color.red);
            int colorTo = getResources().getColor(R.color.lightgray);
            ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
            colorAnimation.setDuration(1000); // time in milliseconds
            colorAnimation.addUpdateListener(animator -> et_maininput.setHintTextColor((int) animator.getAnimatedValue()));
            colorAnimation.start();
            dont_close_soft_keyboard = true;
        }

        return dont_close_soft_keyboard;
    }

    private boolean updateScreen() { //screen update functionality
        String input = this.et_maininput.getText().toString().toUpperCase();
        DBData data = hashmap.get(input);

        if (data != null) { // refresh screen elements
            tv_bundesland.setText(data.land);
            tv_stadtlandkreis.setText(String.join(",\n", data.kreis));
            tv_abgeleitetvon.setText(data.ableitung);
            placeBlob(data.n, data.e);

            //update links
            this.ll_links.removeAllViews();
            for (View v: this.views_quellen) {
                this.ll_quellen.removeView(v);
            }
            if (data.n != 0 && data.e != 0) {
                this.views_quellen.add(addLink(String.format(getString(R.string.coords), input),
                        String.format(getString(R.string.wikipedia_pre), data.link),
                        this.ll_quellen, 0));
            }
            for (String[] tuple: data.links) {
                addLink(tuple[0], String.format(getString(R.string.wikipedia_pre), tuple[1]), this.ll_links, -1);
            }
            return true;
        }
        return false;
    }

    private void placeBlob(float n, float e) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) vw_locationblob.getLayoutParams();

        float[] vec = mercatorProjection(n, e);
        //calculate map placement
        int leftMargin =  Math.round( (this.mapWidth * (vec[0] - bl[0])) / (tr[0] - bl[0]) - (this.blobWidth / 2f));
        int bottomMargin =  Math.round(((this.mapHeight * (vec[1] - bl[1])) / (tr[1] - bl[1])) - (this.blobHeight / 2f));

        Log.e("GD", Integer.toString(this.blobWidth / 2));
        Log.e("GD", Integer.toString(this.blobHeight / 2));

        params.setMargins(leftMargin, 0,0,bottomMargin);

        vw_locationblob.setLayoutParams(params);
        vw_locationblob.setVisibility(View.VISIBLE);
    }

    private float[] mercatorProjection(float n, float e) {
        float[] vec = {0, 0};

        if (n == 0) {
            return vec;
        }

        vec[0] = (float) Math.toRadians(e);
        vec[1] = (float) Math.log(Math.tan(Math.toRadians(n)));
        return vec;
    }

    private View addLink(String text, String link, LinearLayout ll_linksquellen, int index) {
        //adds a view with layout link_format.xml to a linear layout
        View source = inflater.inflate(R.layout.link_format, null);
        ((TextView) source.findViewById(R.id.title)).setText(text);
        ((TextView) source.findViewById(R.id.link)).setText(link);
        source.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setData(Uri.parse(link));
                startActivity(intent);
            }
        });
        source.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                ClipData clip = ClipData.newPlainText(null, link);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(source.getContext(), getString(R.string.zwischenablage),Toast.LENGTH_SHORT).show();
                return true;
            }
        });
        if (index == -1) {
            ll_linksquellen.addView(source);
        } else {
            ll_linksquellen.addView(source, index);
        }
        return source;
    }

    private boolean switchToMapScreen() {
        if (efab_floating.getText().equals(getString(R.string.arrow_back))) { // on linksquelle screen
            vw_switcher.showNext();
            efab_floating.setText(getString(R.string.arrow_tl_bar));
            return false;
        }
        return true;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // get dimensions
        this.mapWidth = this.iv_map.getWidth();
        this.mapHeight = this.iv_map.getHeight();
        this.blobWidth = this.vw_locationblob.getWidth();
        this.blobHeight = this.vw_locationblob.getHeight();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return switchToMapScreen();
        }

        return super.onKeyDown(keyCode, event);
    }
}