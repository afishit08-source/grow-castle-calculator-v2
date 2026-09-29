package com.growcalculate;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import org.json.*;
import java.io.*;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    JSONObject meta;
    JSONObject heroes;
    JSONObject towers;
    JSONObject progression;
    JSONObject towerItems;
    LinearLayout root;
    LinearLayout resultContainer;
    Spinner buildSpinner;
    EditText waveInput;
    ArrayList<JSONObject> builds=new ArrayList<>();

    int dp(float v){
        return (int)(v*getResources().getDisplayMetrics().density+0.5f);
    }

    TextView tv(String s,int size){
        TextView t=new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(Color.WHITE);
        t.setPadding(dp(8),dp(5),dp(8),dp(5));
        return t;
    }

    String asset(String name)throws Exception{
        InputStream in=getAssets().open(name);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] b=new byte[8192];
        int n;
        while((n=in.read(b))!=-1)out.write(b,0,n);
        in.close();
        return out.toString("UTF-8");
    }

    JSONObject load(String name)throws Exception{
        return new JSONObject(asset(name));
    }

    long checkpoint(JSONObject o,int wave)throws Exception{
        int[] ws={1000,2500,5000,7500,10000};
        if(wave<=1000)return o.getInt("1000");
        if(wave>=10000)return o.getInt("10000");
        for(int i=0;i<ws.length-1;i++){
            if(wave>=ws[i]&&wave<=ws[i+1]){
                int low=ws[i];
                int high=ws[i+1];
                int lv=o.getInt(String.valueOf(low));
                int hv=o.getInt(String.valueOf(high));
                double p=(wave-low)/(double)(high-low);
                return Math.round(lv+(hv-lv)*p);
            }
        }
        return o.getInt("10000");
    }

    long scalable(JSONObject o,int wave)throws Exception{
        if(o.has("cap"))return o.getLong("cap");
        if(wave<=10000)return checkpoint(o,wave);
        double ratio=o.optDouble("post_10000_ratio",0.01);
        double value=o.getDouble("10000")+(wave-10000)*ratio;
        return Math.max(o.getLong("10000"),Math.round(value));
    }

    long castleLevel(int wave)throws Exception{
        JSONObject p=progression.getJSONObject("post_10000_ratio");
        if(wave<=10000){
            JSONObject o=new JSONObject();
            JSONArray a=progression.getJSONArray("checkpoints");
            for(int i=0;i<a.length();i++){
                JSONObject c=a.getJSONObject(i);
                o.put(String.valueOf(c.getInt("wave")),c.getInt("castle"));
            }
            return checkpoint(o,wave);
        }
        return Math.round(400+(wave-10000)*p.getDouble("castle"));
    }

    long townArcherLevel(int wave)throws Exception{
        JSONObject p=progression.getJSONObject("post_10000_ratio");
        if(wave<=10000){
            JSONObject o=new JSONObject();
            JSONArray a=progression.getJSONArray("checkpoints");
            for(int i=0;i<a.length();i++){
                JSONObject c=a.getJSONObject(i);
                o.put(String.valueOf(c.getInt("wave")),c.getInt("town_archer"));
            }
            return checkpoint(o,wave);
        }
        return Math.round(1000+(wave-10000)*p.getDouble("town_archer"));
    }

    String fmt(long n){
        if(n>=1000000000L)return String.format(Locale.US,"%.2fB",n/1000000000.0);
        if(n>=1000000L)return String.format(Locale.US,"%.2fM",n/1000000.0);
        if(n>=1000L)return String.format(Locale.US,"%,d",n);
        return String.valueOf(n);
    }

    long levelForTower(String name,int wave)throws Exception{
        JSONObject all=towers.getJSONObject("towers");
        if(!all.has(name))return 0;
        return scalable(all.getJSONObject(name),wave);
    }

    long levelForHero(String name,int wave)throws Exception{
        JSONObject all=heroes.getJSONObject("heroes");
        if(!all.has(name))return 0;
        return scalable(all.getJSONObject(name),wave);
    }

    long levelForLeader(String name,int wave)throws Exception{
        JSONObject all=heroes.getJSONObject("leaders");
        if(!all.has(name))return 0;
        return scalable(all.getJSONObject(name),wave);
    }

    void sectionResult(String title){
        TextView t=tv(title,18);
        t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(8),dp(14),dp(8),dp(6));
        resultContainer.addView(t);
    }

    void rowResult(String name,String value){
        LinearLayout r=new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(dp(2),dp(1),dp(2),dp(1));

        TextView a=tv(name,14);
        TextView b=tv(value,14);

        a.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        b.setGravity(Gravity.RIGHT);
        b.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));

        r.addView(a);
        r.addView(b);
        resultContainer.addView(r);
    }

    boolean hasStone(JSONObject build)throws Exception{
        JSONArray hs=build.getJSONArray("heroes");
        for(int i=0;i<hs.length();i++){
            if(hs.getString(i).equalsIgnoreCase("Stone"))return true;
        }
        return false;
    }

    int getTowerSlots(JSONObject build)throws Exception{
        return hasStone(build)?6:4;
    }

    void showBuild(int index,int wave){
        resultContainer.removeAllViews();

        if(index<0||index>=builds.size())return;

        JSONObject build=builds.get(index);

        try{
            sectionResult(build.getString("name"));
            rowResult("Wave",fmt(wave));
            rowResult("Version","Grow Castle 1.50.14");
            rowResult("Style",build.getString("style"));

            sectionResult("CORE");
            rowResult("Castle","Lv."+fmt(castleLevel(wave)));
            rowResult("Town Archer","Lv."+fmt(townArcherLevel(wave)));

            sectionResult("LEADER 1/1");
            String leader=build.getString("leader");
            rowResult("Leader • "+leader,"Lv."+fmt(levelForLeader(leader,wave)));

            sectionResult("HEROES 12/12");
            JSONArray hs=build.getJSONArray("heroes");

            for(int i=0;i<hs.length();i++){
                String h=hs.getString(i);
                String extra=h.equalsIgnoreCase("Stone")?" • +2 Tower Slots":"";
                rowResult("Slot "+(i+1)+" • "+h+extra,"Lv."+fmt(levelForHero(h,wave)));
            }

            int towerSlots=getTowerSlots(build);

            sectionResult("TOWERS "+towerSlots+"/"+towerSlots);

            JSONArray ts;

            if(towerSlots==6){
                ts=build.getJSONArray("towers_with_stone");
            }else{
                ts=build.getJSONArray("towers_normal");
            }

            for(int i=0;i<towerSlots;i++){
                if(i<ts.length()){
                    String tower=ts.getString(i);
                    rowResult("Slot "+(i+1)+" • "+tower,"Lv."+fmt(levelForTower(tower,wave)));
                }else{
                    rowResult("Slot "+(i+1),"EMPTY");
                }
            }

            if(towerSlots==6){
                rowResult("Stone Bonus","+2 Tower Slots");
            }

            sectionResult("TOWN 3/3");

            JSONArray town=build.getJSONArray("town");

            for(int i=0;i<town.length();i++){
                rowResult("Slot "+(i+1),town.getString(i));
            }

            sectionResult("CASTLE DEFENSE TOP 3/3");

            JSONArray top=build.getJSONArray("castle_defense_top");

            for(int i=0;i<top.length();i++){
                rowResult("Top "+(i+1),top.getString(i));
            }

            sectionResult("CASTLE DEFENSE BOTTOM 1/1");

            JSONArray bottom=build.getJSONArray("castle_defense_bottom");

            for(int i=0;i<bottom.length();i++){
                rowResult("Bottom "+(i+1),bottom.getString(i));
            }

            sectionResult("TREASURES 5/5");

            JSONArray tr=build.getJSONArray("treasures");

            for(int i=0;i<tr.length();i++){
                rowResult("Slot "+(i+1),tr.getString(i));
            }

            sectionResult("TOWER ITEMS");

            rowResult(
                "Tower Equipment",
                towerItems.optBoolean("tower_items_supported",false)?"SUPPORTED":"NOT SUPPORTED"
            );

        }catch(Exception e){
            rowResult("Error",e.getMessage()==null?"Unknown error":e.getMessage());
        }
    }

    int readWave(){
        String value=waveInput.getText().toString().trim();

        if(value.length()==0)return 10000;

        try{
            long parsed=Long.parseLong(value);

            if(parsed<1)parsed=1;

            if(parsed>Integer.MAX_VALUE)parsed=Integer.MAX_VALUE;

            return (int)parsed;

        }catch(Exception e){
            return 10000;
        }
    }

    @Override
    protected void onCreate(Bundle b){
        super.onCreate(b);

        try{
            meta=load("meta_2026.json");
            heroes=load("hero_levels_2026.json");
            towers=load("tower_levels_2026.json");
            progression=load("progression.json");
            towerItems=load("tower_items_2026.json");
        }catch(Exception e){
            Toast.makeText(this,"Database error: "+e.getMessage(),Toast.LENGTH_LONG).show();
            return;
        }

        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(16,16,16));
        root.setPadding(dp(12),dp(12),dp(12),dp(30));

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);

        TextView title=tv("GrowCalculate",25);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        root.addView(title);

        TextView subtitle=tv(
            "Grow Castle 1.50.14 • Planner 2026",
            13
        );
        root.addView(subtitle);

        TextView waveLabel=tv("WAVE",13);
        waveLabel.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        root.addView(waveLabel);

        waveInput=new EditText(this);
        waveInput.setText("10000");
        waveInput.setHint("Masukkan Wave");
        waveInput.setTextColor(Color.WHITE);
        waveInput.setHintTextColor(Color.GRAY);
        waveInput.setSingleLine(true);
        waveInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(waveInput);

        TextView buildLabel=tv("BUILD",13);
        buildLabel.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        root.addView(buildLabel);

        buildSpinner=new Spinner(this);

        try{
            JSONArray bs=meta.getJSONArray("builds");

            for(int i=0;i<bs.length();i++){
                builds.add(bs.getJSONObject(i));
            }

            ArrayList<String> names=new ArrayList<>();

            for(JSONObject x:builds){
                names.add(x.getString("name"));
            }

            ArrayAdapter<String> adapter=
                new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_spinner_dropdown_item,
                    names
                );

            buildSpinner.setAdapter(adapter);

        }catch(Exception e){
            Toast.makeText(this,"Build database error: "+e.getMessage(),Toast.LENGTH_LONG).show();
        }

        root.addView(buildSpinner);

        Button calculate=new Button(this);
        calculate.setText("CALCULATE BUILD");
        root.addView(calculate);

        resultContainer=new LinearLayout(this);
        resultContainer.setOrientation(LinearLayout.VERTICAL);
        resultContainer.setPadding(dp(2),dp(10),dp(2),dp(20));

        root.addView(resultContainer);

        calculate.setOnClickListener(v->{
            int wave=readWave();
            int selected=buildSpinner.getSelectedItemPosition();

            if(selected<0)selected=0;

            showBuild(selected,wave);
        });

        buildSpinner.setOnItemSelectedListener(
            new AdapterView.OnItemSelectedListener(){
                @Override
                public void onItemSelected(
                    AdapterView<?> parent,
                    View view,
                    int position,
                    long id
                ){
                    showBuild(position,readWave());
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent){}
            }
        );

        waveInput.setOnEditorActionListener((v,actionId,event)->{
            showBuild(
                Math.max(0,buildSpinner.getSelectedItemPosition()),
                readWave()
            );
            return false;
        });

        setContentView(scroll);

        showBuild(0,10000);
    }
}