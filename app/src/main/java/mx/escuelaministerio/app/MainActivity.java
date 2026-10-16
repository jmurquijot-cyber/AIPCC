package mx.escuelaministerio.app;

import android.annotation.SuppressLint;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressLint("SetTextI18n")
public final class MainActivity extends Activity {
    private static final int REQUEST_IMPORT_EXCEL = 310;
    private static final int REQUEST_IMPORT_HISTORY = 311;
    private static final int REQUEST_IMPORT_LIFE_MINISTRY = 312;
    private static final int REQUEST_SUPPORT_RESTORE = 313;
    private static final int REQUEST_SUPPORT_SAVE = 314;
    private static final String EXCEL_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private MinisterioDb db;
    private SharedPreferences palette;
    private SharedPreferences appState;
    private FrameLayout content;
    private String selectedMonth = YearMonth.now().toString();
    private String activeWeek = "";
    private boolean showActiveWeekOnly;
    private boolean inModule;
    private String moduleMode = "HOME";
    private SupportAuth.User supportUser;
    private File pendingSupportBackup;
    private int pendingHistoricalYear = 2026;
    private final Locale es = new Locale("es", "MX");

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new MinisterioDb(this);
        palette = getSharedPreferences("palette", MODE_PRIVATE);
        appState = getSharedPreferences("app_state", MODE_PRIVATE);
        activeWeek = appState.getString("active_week", "");
        showActiveWeekOnly = appState.getBoolean("show_active_week_only", false);
        requestNotificationPermission();
        showHome();
    }

    private void requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 40);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int palette(String key, String fallback) {
        try { return Color.parseColor(palette.getString(key, fallback)); }
        catch (Exception e) { return Color.parseColor(fallback); }
    }

    private void showHome() {
        supportUser=null;
        inModule = false;
        moduleMode = "HOME";
        LinearLayout root = column();
        root.setBackgroundColor(Color.rgb(5, 18, 31));

        TextView heading = label("ADMINISTRACIÓN DE ASIGNACIONES PARA LAS REUNIONES CRISTIANAS", 18, true);
        heading.setTextColor(Color.WHITE);
        heading.setGravity(Gravity.CENTER);
        heading.setPadding(dp(18), dp(16), dp(18), dp(16));
        heading.setBackgroundColor(Color.rgb(7, 38, 62));
        root.addView(heading, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        LinearLayout body = column();
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(dp(14), dp(14), dp(14), dp(28));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo_aaprc);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        logo.setContentDescription("Logotipo AAPRC");
        logo.setClickable(true);
        logo.setOnClickListener(v->showSupportLogin());
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(-1, dp(210));
        logoParams.setMargins(0, 0, 0, dp(8));
        body.addView(logo, logoParams);

        TextView modules = label("Módulos", 20, true);
        modules.setTextColor(Color.WHITE);
        modules.setGravity(Gravity.CENTER);
        modules.setPadding(dp(4), 0, dp(4), dp(8));
        body.addView(modules, new LinearLayout.LayoutParams(-1, -2));

        body.addView(homeModuleCard(R.drawable.module_escuela, "Escuela del Ministerio Teocrático", "Abrir módulo", true,
                v -> { buildShell(); showProgram(); }));
        body.addView(homeModuleCard(R.drawable.module_vida_ministerio, "Vida y Ministerio", "Abrir módulo", true,
                v -> { buildLifeMinistryShell(); showLifeMinistryProgram(); }));
        body.addView(homeModuleCard(R.drawable.module_reunion_publica, "Reunión Pública y Atalaya", "Abrir módulo", true,
                v -> {buildPublicShell();showPublicProgram();}));
        body.addView(homeModuleCard(R.drawable.module_asignaciones_mecanicas, "Asignaciones Mecánicas", "Abrir módulo", true,
                v -> { buildMechanicalShell(); showMechanicalProgram(); }));
        body.addView(homeModuleCard(R.drawable.module_predicacion, "Predicación", "Campaña pública disponible", true,
                v -> {buildPreachingShell();showPreachingHome();}));

        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private View homeModuleCard(int imageResource, String title, String status, boolean available, View.OnClickListener listener) {
        LinearLayout card = row();
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), dp(10), dp(12), dp(10));
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(listener);
        card.setContentDescription(title + ". " + status);

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(15, 39, 58));
        background.setCornerRadius(dp(12));
        background.setStroke(dp(1), available ? Color.rgb(34, 211, 238) : Color.rgb(71, 85, 105));
        card.setBackground(background);
        if (!available) card.setAlpha(0.88f);

        ImageView image = new ImageView(this);
        image.setImageResource(imageResource);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setContentDescription(null);
        card.addView(image, new LinearLayout.LayoutParams(dp(92), dp(78)));

        LinearLayout text = column();
        text.setPadding(dp(14), 0, 0, 0);
        TextView name = label(title, 17, true);
        name.setTextColor(Color.WHITE);
        TextView state = label(status, 13, false);
        state.setTextColor(available ? Color.rgb(103, 232, 249) : Color.rgb(148, 163, 184));
        text.addView(name);
        text.addView(state);
        card.addView(text, new LinearLayout.LayoutParams(0, -2, 1));

        TextView arrow = label(available ? "›" : "", 28, true);
        arrow.setTextColor(Color.rgb(103, 232, 249));
        card.addView(arrow);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(5), 0, dp(5));
        card.setLayoutParams(params);
        return card;
    }

    private void buildShell() {
        inModule = true;
        moduleMode = "SCHOOL";
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(246,248,250));
        LinearLayout banner = row(); banner.setGravity(Gravity.CENTER_VERTICAL); banner.setPadding(dp(12),dp(8),dp(12),dp(8)); banner.setBackgroundColor(palette("title","#00B050"));
        ImageView icon = new ImageView(this); icon.setImageResource(R.drawable.app_icon); icon.setScaleType(ImageView.ScaleType.CENTER_CROP); banner.addView(icon, new LinearLayout.LayoutParams(dp(52),dp(52)));
        TextView title = label("Escuela del Ministerio Teocrático",20,true); title.setTextColor(Color.WHITE); title.setPadding(dp(12),0,0,0); banner.addView(title,new LinearLayout.LayoutParams(0,dp(56),1)); root.addView(banner);
        HorizontalScrollView navScroll = new HorizontalScrollView(this); navScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout nav = row(); nav.setPadding(dp(5),dp(5),dp(5),dp(5)); nav.setBackgroundColor(Color.WHITE);
        nav.addView(navButton("Inicio", v -> showHome()));
        nav.addView(navButton("Programa", v -> showProgram()));
        nav.addView(navButton("Alumnos", v -> showStudents()));
        nav.addView(navButton("Presentaciones", v -> showPresentations()));
        nav.addView(navButton("Presidentes", v -> showPresidents()));
        nav.addView(navButton("Historial", v -> showHistory()));
        nav.addView(navButton("Colores", v -> showSettings()));
        navScroll.addView(nav); root.addView(navScroll,new LinearLayout.LayoutParams(-1,dp(55)));
        content = new FrameLayout(this); root.addView(content,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
    }

    private void buildLifeMinistryShell(){
        inModule=true;moduleMode="LIFE";LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(246,248,250));LinearLayout banner=row();banner.setGravity(Gravity.CENTER_VERTICAL);banner.setPadding(dp(12),dp(8),dp(12),dp(8));banner.setBackgroundColor(palette("title","#00B050"));ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.module_vida_ministerio);icon.setScaleType(ImageView.ScaleType.CENTER_CROP);banner.addView(icon,new LinearLayout.LayoutParams(dp(60),dp(52)));TextView title=label("Vida y Ministerio",20,true);title.setTextColor(Color.WHITE);title.setPadding(dp(12),0,0,0);banner.addView(title,new LinearLayout.LayoutParams(0,dp(56),1));root.addView(banner);HorizontalScrollView navScroll=new HorizontalScrollView(this);navScroll.setHorizontalScrollBarEnabled(false);LinearLayout nav=row();nav.setPadding(dp(5),dp(5),dp(5),dp(5));nav.setBackgroundColor(Color.WHITE);nav.addView(navButton("Inicio",v->showHome()));nav.addView(navButton("Programa",v->showLifeMinistryProgram()));nav.addView(navButton("Alumnos",v->showStudents()));nav.addView(navButton("Historial",v->showLifeMinistryHistory()));nav.addView(navButton("Colores",v->showSettings()));navScroll.addView(nav);root.addView(navScroll,new LinearLayout.LayoutParams(-1,dp(55)));content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }

    private void buildMechanicalShell(){
        inModule=true;moduleMode="MECHANICAL";
        LinearLayout root=column();root.setBackgroundColor(Color.rgb(246,248,250));
        LinearLayout banner=row();banner.setGravity(Gravity.CENTER_VERTICAL);banner.setPadding(dp(12),dp(8),dp(12),dp(8));banner.setBackgroundColor(palette("title","#00B050"));
        ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.module_asignaciones_mecanicas);icon.setScaleType(ImageView.ScaleType.CENTER_CROP);banner.addView(icon,new LinearLayout.LayoutParams(dp(56),dp(52)));
        TextView title=label("Asignaciones Mecánicas",20,true);title.setTextColor(Color.WHITE);title.setPadding(dp(12),0,0,0);banner.addView(title,new LinearLayout.LayoutParams(0,dp(56),1));root.addView(banner);
        HorizontalScrollView navScroll=new HorizontalScrollView(this);navScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout nav=row();nav.setPadding(dp(5),dp(5),dp(5),dp(5));nav.setBackgroundColor(Color.WHITE);
        nav.addView(navButton("Inicio",v->showHome()));nav.addView(navButton("Registro",v->showMechanicalProgram()));nav.addView(navButton("Alumnos",v->showStudents()));nav.addView(navButton("Colores",v->showSettings()));
        navScroll.addView(nav);root.addView(navScroll,new LinearLayout.LayoutParams(-1,dp(55)));
        content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }

    private void buildPublicShell(){
        inModule=true;moduleMode="PUBLIC";
        LinearLayout root=column();root.setBackgroundColor(Color.rgb(246,248,250));
        LinearLayout banner=row();banner.setGravity(Gravity.CENTER_VERTICAL);banner.setPadding(dp(12),dp(8),dp(12),dp(8));banner.setBackgroundColor(palette("title","#00B050"));
        ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.module_reunion_publica);icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        banner.addView(icon,new LinearLayout.LayoutParams(dp(56),dp(52)));
        TextView title=label("Reunión Pública y Atalaya",20,true);title.setTextColor(Color.WHITE);banner.addView(title,new LinearLayout.LayoutParams(0,dp(56),1));root.addView(banner);
        HorizontalScrollView navScroll=new HorizontalScrollView(this);navScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout nav=row();nav.setPadding(dp(5),dp(5),dp(5),dp(5));nav.setBackgroundColor(Color.WHITE);
        nav.addView(navButton("Inicio",v->showHome()));nav.addView(navButton("Programa",v->showPublicProgram()));
        nav.addView(navButton("Oradores visitantes",v->showVisitingSpeakers()));nav.addView(navButton("Historial",v->showPublicHistory()));
        nav.addView(navButton("Alumnos",v->showStudents()));nav.addView(navButton("Colores",v->showSettings()));
        navScroll.addView(nav);root.addView(navScroll,new LinearLayout.LayoutParams(-1,dp(55)));
        content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }

    private void buildPreachingShell(){
        inModule=true;moduleMode="PREACHING";
        LinearLayout root=column();root.setBackgroundColor(Color.rgb(246,248,250));
        LinearLayout banner=row();banner.setGravity(Gravity.CENTER_VERTICAL);banner.setPadding(dp(12),dp(8),dp(12),dp(8));banner.setBackgroundColor(palette("title","#00B050"));
        ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.module_predicacion);icon.setScaleType(ImageView.ScaleType.CENTER_CROP);banner.addView(icon,new LinearLayout.LayoutParams(dp(54),dp(52)));
        TextView title=label("Predicación",20,true);title.setTextColor(Color.WHITE);banner.addView(title,new LinearLayout.LayoutParams(0,dp(56),1));root.addView(banner);
        HorizontalScrollView scroll=new HorizontalScrollView(this);LinearLayout nav=row();nav.setBackgroundColor(Color.WHITE);nav.addView(navButton("Inicio",v->showHome()));nav.addView(navButton("Campañas",v->showPreachingHome()));nav.addView(navButton("Grupos",v->showPreachingGroups()));nav.addView(navButton("Transportes",v->showTransportHome()));nav.addView(navButton("Historial transporte",v->showTransportHistory()));nav.addView(navButton("Alumnos",v->showStudents()));nav.addView(navButton("Colores",v->showSettings()));scroll.addView(nav);root.addView(scroll,new LinearLayout.LayoutParams(-1,dp(55)));
        content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }
    private void rebuildCurrentShell(){if("LIFE".equals(moduleMode))buildLifeMinistryShell();else if("MECHANICAL".equals(moduleMode))buildMechanicalShell();else if("PUBLIC".equals(moduleMode))buildPublicShell();else if("PREACHING".equals(moduleMode))buildPreachingShell();else buildShell();}

    @Override public void onBackPressed() {
        if (inModule) showHome();
        else super.onBackPressed();
    }

    private Button navButton(String text, View.OnClickListener listener) { Button b=button(text,listener); b.setAllCaps(false); return b; }
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView label(String value,int sp,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);t.setTextColor(palette("text","#1F2937"));t.setTypeface(Typeface.DEFAULT,bold?Typeface.BOLD:Typeface.NORMAL);t.setPadding(dp(4),dp(4),dp(4),dp(4));return t;}
    private Button button(String text, View.OnClickListener listener){Button b=new Button(this);b.setText(text);b.setOnClickListener(listener);b.setMinHeight(dp(42));return b;}
    private static final class PersistentDialogBuilder extends AlertDialog.Builder {
        PersistentDialogBuilder(Context context){super(context);}
        @Override public AlertDialog create(){AlertDialog dialog=super.create();dialog.setCancelable(false);dialog.setCanceledOnTouchOutside(false);return dialog;}
        @Override public AlertDialog show(){AlertDialog dialog=create();dialog.show();return dialog;}
    }
    private AlertDialog.Builder safeDialog(){return new PersistentDialogBuilder(this);}
    private EditText input(String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value==null?"":value);e.setSingleLine(true);e.setPadding(dp(8),dp(7),dp(8),dp(7));return e;}
    private EditText dateInput(String value){
        EditText field=input("Fecha",value);field.setFocusable(false);field.setClickable(true);field.setCompoundDrawablesWithIntrinsicBounds(0,0,android.R.drawable.ic_menu_my_calendar,0);field.setCompoundDrawablePadding(dp(8));field.setOnClickListener(v->openDatePicker(field,null));return field;
    }
    private void openDatePicker(EditText field,Runnable afterSelection){
        LocalDate initial;
        try{initial=LocalDate.parse(value(field));}catch(Exception ignored){try{initial=YearMonth.parse(selectedMonth).atDay(1);}catch(Exception ignoredMonth){initial=LocalDate.now();}}
        DatePickerDialog dialog=new DatePickerDialog(this,(picker,year,month,day)->{field.setText(String.format(Locale.ROOT,"%04d-%02d-%02d",year,month+1,day));if(afterSelection!=null)afterSelection.run();},initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth());
        dialog.setCanceledOnTouchOutside(false);dialog.show();
    }
    private String selectedMonthLabel(){
        YearMonth value;try{value=YearMonth.parse(selectedMonth);}catch(Exception ignored){value=YearMonth.now();}
        String month=value.getMonth().getDisplayName(TextStyle.FULL,es);return month.substring(0,1).toUpperCase(es)+month.substring(1)+" de "+value.getYear();
    }
    private void openMonthPicker(Runnable afterSelection){
        YearMonth initial;try{initial=YearMonth.parse(selectedMonth);}catch(Exception ignored){initial=YearMonth.now();}
        String[] monthNames=new String[12];for(int i=0;i<12;i++)monthNames[i]=YearMonth.of(2000,i+1).getMonth().getDisplayName(TextStyle.FULL,es);
        NumberPicker month=new NumberPicker(this);month.setMinValue(1);month.setMaxValue(12);month.setDisplayedValues(monthNames);month.setValue(initial.getMonthValue());month.setWrapSelectorWheel(true);month.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        NumberPicker year=new NumberPicker(this);year.setMinValue(2000);year.setMaxValue(2100);year.setValue(initial.getYear());year.setWrapSelectorWheel(false);year.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        LinearLayout form=row();form.setGravity(Gravity.CENTER);form.setPadding(dp(12),dp(8),dp(12),dp(8));form.addView(month,new LinearLayout.LayoutParams(0,-2,2));form.addView(year,new LinearLayout.LayoutParams(0,-2,1));
        safeDialog().setTitle("Seleccionar mes del programa").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Aceptar",(dialog,which)->{selectedMonth=String.format(Locale.ROOT,"%04d-%02d",year.getValue(),month.getValue());if(afterSelection!=null)afterSelection.run();}).show();
    }
    private Spinner spinner(List<?> values){Spinner s=new Spinner(this);ArrayAdapter<Object>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new ArrayList<>(values));s.setAdapter(a);return s;}
    private Spinner songSpinner(String saved){
        List<Models.Song> choices=new ArrayList<>();choices.add(new Models.Song(0,"Sin seleccionar"));choices.addAll(db.songs());
        Spinner picker=spinner(choices);selectSong(picker,saved);
        return picker;
    }
    private void selectSong(Spinner picker,String saved){
        String previous=saved==null?"":saved.trim();
        ArrayAdapter<Models.Song> adapter=(ArrayAdapter<Models.Song>)picker.getAdapter();
        for(int i=adapter.getCount()-1;i>=0;i--)if(adapter.getItem(i).number<0)adapter.remove(adapter.getItem(i));
        if(previous.isEmpty()){picker.setSelection(0);return;}
        for(int i=1;i<picker.getAdapter().getCount();i++){
            Models.Song item=(Models.Song)picker.getAdapter().getItem(i);
            if(previous.equals(String.valueOf(item.number))||previous.equalsIgnoreCase(item.title)||previous.equalsIgnoreCase(item.toString())||previous.startsWith(item.number+" · ")){picker.setSelection(i);return;}
        }
        // A historic free-text value must remain selectable until the user replaces it.
        adapter.add(new Models.Song(-1,previous));
        picker.setSelection(adapter.getCount()-1);
    }
    private String selectedSong(Spinner picker){Models.Song song=(Models.Song)picker.getSelectedItem();return song.number==0?"":song.number<0?song.title:song.toString();}
    private void selectById(Spinner spinner,List<?> items,long id){for(int i=0;i<items.size();i++){Object x=items.get(i);long itemId=x instanceof Models.Student?((Models.Student)x).id:x instanceof Models.President?((Models.President)x).id:x instanceof Models.VisitingSpeaker?((Models.VisitingSpeaker)x).id:x instanceof Models.Presentation?((Models.Presentation)x).id:0;if(itemId==id){spinner.setSelection(i);break;}}}
    private ScrollView page(String title){content.removeAllViews();ScrollView scroll=new ScrollView(this);LinearLayout body=column();body.setPadding(dp(12),dp(10),dp(12),dp(30));TextView heading=label(title,22,true);body.addView(heading);scroll.addView(body);scroll.setTag(body);content.addView(scroll);return scroll;}
    private LinearLayout body(ScrollView page){return (LinearLayout)page.getTag();}
    private View divider(){View v=new View(this);v.setBackgroundColor(Color.LTGRAY);v.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(1)));return v;}
    private LinearLayout card(){LinearLayout c=column();c.setPadding(dp(10),dp(8),dp(10),dp(8));c.setBackgroundColor(Color.WHITE);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(4),0,dp(4));c.setLayoutParams(p);return c;}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private String value(EditText e){return e.getText().toString().trim();}
    private int intValue(EditText e,int fallback){try{return Integer.parseInt(value(e));}catch(Exception ignored){return fallback;}}

    private void showPreachingHome(){
        ScrollView p=page("Predicación");LinearLayout b=body(p);
        b.addView(label("Campaña de Predicación Pública",18,true));
        b.addView(label("1. Configure fechas, jornada y exhibidores. Los turnos se calculan al guardar.\n2. Después, asigne por separado cuatro publicadores por exhibidor y turno.",14,false));
        b.addView(button("Configurar nueva campaña",v->editPreachingCampaign(null)));
        List<PreachingCampaign.Campaign> campaigns=new PreachingCampaign(db).all();
        for(PreachingCampaign.Campaign campaign:campaigns){LinearLayout c=card();c.addView(label(campaign.toString()+" · "+campaign.congregation,16,true));
            c.addView(label("Jornada "+PreachingCampaign.time(campaign.startMinute)+"–"+PreachingCampaign.time(campaign.endMinute)+" · "+campaign.slots()+" turnos de dos horas",14,false));
            c.addView(button("Llenar publicadores",v->showPreachingRoster(campaign,campaign.startDate)));
            c.addView(button("Editar datos de la campaña",v->editPreachingCampaign(campaign)));
            c.addView(button("Imagen del programa semanal",v->choosePreachingWeek(campaign)));
            c.addView(button("Imagen del turno",v->choosePreachingShift(campaign)));
            c.addView(button("Eliminar campaña",v->safeDialog().setTitle("¿Eliminar esta campaña?").setMessage("Se eliminarán definitivamente sus turnos, publicadores, capitanes y transportes. No afectará otros módulos.").setNegativeButton("Cancelar",null).setPositiveButton("Eliminar",(d,w)->{new PreachingCampaign(db).remove(campaign.id);showPreachingHome();}).show()));b.addView(c);
        }
        if(campaigns.isEmpty())b.addView(label("No hay campañas configuradas todavía.",14,false));
        b.addView(divider());b.addView(button("Administrar Grupos de Predicación",v->showPreachingGroups()));b.addView(button("Abrir Programa de Transporte",v->showTransportHome()));b.addView(label("Próximas partes",17,true));
        b.addView(label("Programa semanal de la congregación · Visita de circuito · Limpieza del Salón del Reino. Estas partes se definirán más adelante.",13,false));
    }

    private void showPreachingGroups(){
        ScrollView page=page("Grupos de Predicación");LinearLayout body=body(page);PreachingGroups repository=new PreachingGroups(db);body.addView(label("Organice a los alumnos por grupo y defina sus responsables y punto de encuentro.",14,false));body.addView(button("Crear grupo",v->editPreachingGroup(null)));
        List<PreachingGroups.Group> groups=repository.all();if(groups.isEmpty())body.addView(label("Aún no hay grupos registrados.",14,false));
        for(PreachingGroups.Group group:groups){List<Models.Student> members=repository.members(group.number,true);LinearLayout card=card();card.addView(label("Grupo "+group.number+" · "+group.name,18,true));card.addView(label("Capitán: "+repository.studentName(group.captainId)+"\nAuxiliar: "+repository.studentName(group.assistantId),14,false));String point=group.meetingPointName.isEmpty()?"Punto de encuentro pendiente":group.meetingPointName+" · "+group.meetingPointAddress;card.addView(label(point+(group.meetingPointPhone.isEmpty()?"":" · Tel. "+group.meetingPointPhone),13,false));card.addView(label("Integrantes: "+members.size()+" · Precursores Regulares: "+repository.pioneerCount(group.number),13,true));card.addView(button("Abrir grupo",v->showPreachingGroup(group.number)));body.addView(card);}
        List<Models.Student> unassigned=repository.unassigned();body.addView(divider());body.addView(label("Alumnos sin grupo: "+unassigned.size(),16,true));for(Models.Student student:unassigned)body.addView(label(student.name+(student.pioneer?" · PRECURSOR REGULAR":"")+(student.isSuspended()?" · Suspendido":""),13,false));body.addView(button("Volver a Predicación",v->showPreachingHome()));
    }

    private void editPreachingGroup(PreachingGroups.Group current){
        boolean creating=current==null;PreachingGroups.Group group=creating?new PreachingGroups.Group():current;LinearLayout form=column();form.setPadding(dp(14),dp(6),dp(14),dp(6));EditText number=input("Número de grupo",creating?"":String.valueOf(group.number));number.setInputType(InputType.TYPE_CLASS_NUMBER);number.setEnabled(creating);EditText name=input("Nombre del grupo",group.name);EditText point=input("Nombre del punto de encuentro",group.meetingPointName);EditText address=input("Dirección del punto de encuentro",group.meetingPointAddress);address.setSingleLine(false);address.setMinLines(2);EditText phone=input("Teléfono o celular (opcional)",group.meetingPointPhone);phone.setInputType(InputType.TYPE_CLASS_PHONE);form.addView(label("Número",14,true));form.addView(number);form.addView(label("Nombre del grupo",14,true));form.addView(name);form.addView(label("Punto de encuentro",14,true));form.addView(point);form.addView(label("Dirección",14,true));form.addView(address);form.addView(label("Teléfono o celular",14,true));form.addView(phone);ScrollView scroll=new ScrollView(this);scroll.addView(form);AlertDialog dialog=safeDialog().setTitle(creating?"Crear Grupo de Predicación":"Editar Grupo de Predicación").setView(scroll).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(x->{if(creating)group.number=intValue(number,0);group.name=value(name);group.meetingPointName=value(point);group.meetingPointAddress=value(address);group.meetingPointPhone=value(phone);try{new PreachingGroups(db).save(group,creating);dialog.dismiss();toast(creating?"Grupo creado. Ahora asigne sus integrantes.":"Datos del grupo actualizados.");showPreachingGroup(group.number);}catch(Exception error){toast(error.getMessage());}}));dialog.show();
    }

    private void showPreachingGroup(int groupNumber){
        PreachingGroups repository=new PreachingGroups(db);PreachingGroups.Group group=repository.get(groupNumber);if(group==null){toast("El grupo ya no existe.");showPreachingGroups();return;}ScrollView page=page("Grupo "+group.number+" · "+group.name);LinearLayout body=body(page);body.addView(label("Capitán: "+repository.studentName(group.captainId)+"\nAuxiliar: "+repository.studentName(group.assistantId),15,true));body.addView(label("Punto de encuentro: "+(group.meetingPointName.isEmpty()?"Pendiente":group.meetingPointName)+"\nDirección: "+(group.meetingPointAddress.isEmpty()?"Pendiente":group.meetingPointAddress)+(group.meetingPointPhone.isEmpty()?"":"\nTeléfono: "+group.meetingPointPhone),14,false));body.addView(button("Editar datos del grupo",v->editPreachingGroup(group)));body.addView(button("Asignar integrantes",v->editPreachingGroupMembers(group)));body.addView(button("Elegir capitán y auxiliar",v->editPreachingGroupLeaders(group)));body.addView(divider());List<Models.Student> members=repository.members(group.number,true);body.addView(label("Integrantes · "+members.size(),17,true));if(members.isEmpty())body.addView(label("Todavía no se han asignado integrantes.",14,false));for(Models.Student member:members){String role=member.id==group.captainId?" · CAPITÁN":member.id==group.assistantId?" · AUXILIAR":"";body.addView(label(member.name+role+(member.pioneer?" · PRECURSOR REGULAR":"")+(member.isSuspended()?" · Suspendido":""),14,member.id==group.captainId||member.id==group.assistantId));}body.addView(button("Eliminar grupo",v->safeDialog().setTitle("¿Eliminar el Grupo "+group.number+"?").setMessage("Los alumnos quedarán sin grupo. No se eliminará ningún alumno.").setNegativeButton("Cancelar",null).setPositiveButton("Eliminar",(d,w)->{repository.delete(group);showPreachingGroups();}).show()));body.addView(button("Volver a grupos",v->showPreachingGroups()));
    }

    private void editPreachingGroupMembers(PreachingGroups.Group group){
        ScrollView page=page("Integrantes · Grupo "+group.number);LinearLayout body=body(page);body.addView(label("Marque los alumnos que pertenecerán al grupo. Si alguien estaba en otro grupo, será trasladado a este.",14,false));List<Models.Student> students=db.students(true);List<CheckBox> checks=new ArrayList<>();for(Models.Student student:students){String suffix=student.pioneer?" · PRECURSOR REGULAR":"";if(student.groupNumber>0&&student.groupNumber!=group.number)suffix+=" · Actualmente Grupo "+student.groupNumber;if(student.isSuspended())suffix+=" · Suspendido";CheckBox check=checkBox(student.name+suffix,student.groupNumber==group.number);checks.add(check);body.addView(check);}body.addView(button("Guardar integrantes",v->{List<Long> selected=new ArrayList<>();for(int i=0;i<students.size();i++)if(checks.get(i).isChecked())selected.add(students.get(i).id);try{new PreachingGroups(db).replaceMembers(group,selected);toast("Integrantes del grupo actualizados.");showPreachingGroup(group.number);}catch(Exception error){toast(error.getMessage());}}));body.addView(button("Cancelar",v->showPreachingGroup(group.number)));
    }

    private void editPreachingGroupLeaders(PreachingGroups.Group group){
        PreachingGroups repository=new PreachingGroups(db);List<Models.Student> candidates=repository.members(group.number,false);if(candidates.size()<2){toast("El grupo necesita al menos dos integrantes activos para elegir capitán y auxiliar.");return;}LinearLayout form=column();form.setPadding(dp(14),dp(8),dp(14),dp(8));form.addView(label("Capitán de Grupo",14,true));Spinner captain=spinner(candidates);selectById(captain,candidates,group.captainId);form.addView(captain);form.addView(label("Auxiliar de Grupo",14,true));Spinner assistant=spinner(candidates);selectById(assistant,candidates,group.assistantId);if(group.assistantId==0&&candidates.size()>1)assistant.setSelection(1);form.addView(assistant);AlertDialog dialog=safeDialog().setTitle("Responsables · Grupo "+group.number).setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(x->{try{repository.setLeaders(group,((Models.Student)captain.getSelectedItem()).id,((Models.Student)assistant.getSelectedItem()).id);dialog.dismiss();toast("Capitán y auxiliar guardados.");showPreachingGroup(group.number);}catch(Exception error){toast(error.getMessage());}}));dialog.show();
    }
    private EditText preachingTimeInput(String text){EditText e=input("Hora",text);e.setFocusable(false);e.setClickable(true);e.setOnClickListener(v->{int m;try{m=PreachingCampaign.minute(value(e));}catch(Exception ignored){m=480;}TimePickerDialog picker=new TimePickerDialog(this,(view,hour,min)->e.setText(PreachingCampaign.time(hour*60+min)),m/60,m%60,true);picker.setCanceledOnTouchOutside(false);picker.show();});return e;}
    private void editPreachingCampaign(PreachingCampaign.Campaign old){
        ScrollView p=page(old==null?"Nueva campaña pública":"Datos de la campaña");LinearLayout b=body(p);
        EditText start=dateInput(old==null?LocalDate.now().toString():old.startDate),end=dateInput(old==null?LocalDate.now().plusDays(6).toString():old.endDate);
        EditText startTime=preachingTimeInput(old==null?"08:00":PreachingCampaign.time(old.startMinute)),endTime=preachingTimeInput(old==null?"18:00":PreachingCampaign.time(old.endMinute));
        EditText booths=input("Número de exhibidores",old==null?"1":String.valueOf(old.exhibitors));booths.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText module=input("Módulo o lugar (opcional)",old==null?"":old.module),congregation=input("Congregación",old==null?"":old.congregation);
        b.addView(label("Fecha inicial",14,true));b.addView(start);b.addView(label("Fecha final (inclusive)",14,true));b.addView(end);
        b.addView(label("Inicio de la jornada diaria",14,true));b.addView(startTime);b.addView(label("Fin de la jornada diaria",14,true));b.addView(endTime);
        b.addView(label("Cantidad de exhibidores",14,true));b.addView(booths);b.addView(label("Módulo",14,true));b.addView(module);b.addView(label("Congregación",14,true));b.addView(congregation);
        TextView preview=label("Pulse «Calcular turnos» para revisar el horario.",15,false);b.addView(preview);
        View.OnClickListener calculate=v->{try{PreachingCampaign.Campaign c=campaignFromForm(old,start,end,startTime,endTime,booths,module,congregation);StringBuilder text=new StringBuilder(c.slots()+" turnos diarios por exhibidor:\n");for(int i=0;i<c.slots();i++)text.append(i+1).append(". ").append(c.slotLabel(i)).append("\n");preview.setText(text.toString());}catch(Exception error){preview.setText(error.getMessage());}};
        b.addView(button("Calcular turnos",calculate));calculate.onClick(null);
        b.addView(button("Guardar campaña",v->{try{PreachingCampaign.Campaign c=campaignFromForm(old,start,end,startTime,endTime,booths,module,congregation);new PreachingCampaign(db).save(c);toast("Campaña y turnos configurados. Ahora llene los publicadores por separado.");showPreachingHome();}catch(Exception error){toast(error.getMessage());}}));
        b.addView(button("Cancelar",v->showPreachingHome()));
    }
    private PreachingCampaign.Campaign campaignFromForm(PreachingCampaign.Campaign old,EditText start,EditText end,EditText startTime,EditText endTime,EditText booths,EditText module,EditText congregation){
        PreachingCampaign.Campaign c=new PreachingCampaign.Campaign();if(old!=null)c.id=old.id;c.startDate=value(start);c.endDate=value(end);c.startMinute=PreachingCampaign.minute(value(startTime));c.endMinute=PreachingCampaign.minute(value(endTime));c.exhibitors=intValue(booths,0);c.module=value(module);c.congregation=value(congregation);PreachingCampaign.validate(c);return c;
    }
    private void showPreachingRoster(PreachingCampaign.Campaign c,String day){
        try{PreachingCampaign.validateDay(c,day);}catch(Exception error){toast(error.getMessage());return;}
        ScrollView p=page("Publicadores · "+c.congregation);LinearLayout b=body(p);EditText selector=dateInput(day);
        selector.setOnClickListener(v->openDatePicker(selector,()->{String picked=value(selector);try{PreachingCampaign.validateDay(c,picked);showPreachingRoster(c,picked);}catch(Exception error){selector.setText(day);toast(error.getMessage());}}));
        b.addView(label("Fecha de la campaña",14,true));b.addView(selector);
        b.addView(label("Capture hasta cuatro publicadores por módulo y turno. Con dos publicadores el módulo ya es viable. Una persona puede repetir en otro turno.",14,false));
        PreachingCampaign repo=new PreachingCampaign(db);
        int total=c.slots()*c.exhibitors*4,filled=repo.dayPublisherCount(c,day);PreachingCampaign.DayResponsibilities responsibilities=repo.responsibilities(c,day);
        int nonViable=repo.nonViableModules(c,day);
        b.addView(label("Publicadores capturados: "+filled+"/"+total+" · Módulos no viables: "+nonViable,15,true));
        if(repo.legacyCaptainCount(c,day)>1)b.addView(label("La versión anterior tenía varios capitanes de turno para este día. Elija ahora un solo capitán del día.",13,false));
        for(int shift=0;shift<c.slots();shift++){final int slot=shift;LinearLayout header=card();header.addView(label(c.slotLabel(slot),17,true));b.addView(header);
            for(int booth=1;booth<=c.exhibitors;booth++){final int exhibitor=booth;PreachingCampaign.Team team=repo.team(c,day,slot,booth);LinearLayout card=card();card.addView(label("Módulo "+booth+" · "+assignedCount(team)+"/4 publicadores"+(assignedCount(team)>=2?" · VIABLE":" · MÍNIMO 2"),15,true));
                card.addView(button("Asignar publicadores",v->editPreachingTeam(c,day,slot,exhibitor)));b.addView(card);}}
        b.addView(button("Asignar capitán y transporte",v->editPreachingDay(c,day)));
        b.addView(button("Volver a campañas",v->showPreachingHome()));
    }
    private int assignedCount(PreachingCampaign.Team t){int count=0;for(long id:t.publishers)if(id!=0)count++;return count;}
    private List<Models.Student> preachingChoices(){List<Models.Student> choices=new ArrayList<>();Models.Student none=new Models.Student();none.name="Sin asignar";choices.add(none);choices.addAll(db.students(false));return choices;}
    private void editPreachingTeam(PreachingCampaign.Campaign c,String day,int slot,int booth){
        PreachingCampaign repo=new PreachingCampaign(db);PreachingCampaign.Team existing=repo.team(c,day,slot,booth);List<Models.Student> choices=preachingChoices();
        if(choices.size()==1){toast("Registre alumnos activos antes de asignar publicadores.");return;}
        LinearLayout form=column();form.setPadding(dp(14),dp(4),dp(14),dp(4));Spinner[] people=new Spinner[4];
        for(int i=0;i<4;i++){form.addView(label("Publicador "+(i+1),14,true));people[i]=spinner(choices);selectById(people[i],choices,existing.publishers[i]);form.addView(people[i]);}
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=safeDialog().setTitle(day+" · "+c.slotLabel(slot)+" · Exhibidor "+booth).setView(scroll).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(x->{PreachingCampaign.Team team=new PreachingCampaign.Team();for(int i=0;i<4;i++)team.publishers[i]=((Models.Student)people[i].getSelectedItem()).id;
            try{repo.saveTeam(c,day,slot,booth,team);dialog.dismiss();showPreachingRoster(c,day);}catch(Exception error){toast(error.getMessage());}}));dialog.show();
    }
    private void editPreachingDay(PreachingCampaign.Campaign c,String day){
        PreachingCampaign repo=new PreachingCampaign(db);
        if(!repo.modulesViable(c,day)){toast("Cada módulo debe tener al menos dos publicadores para poder asignar el capitán y el transporte.");return;}
        PreachingCampaign.DayResponsibilities saved=repo.responsibilities(c,day);List<Models.Student> captains=db.students(false);captains.removeIf(student->!repo.isDayPublisher(c,day,student.id));
        if(captains.isEmpty()){toast("No hay publicadores activos disponibles para elegir al capitán del día.");return;}
        List<Models.Student> choices=preachingChoices();LinearLayout form=column();form.setPadding(dp(14),dp(6),dp(14),dp(6));
        form.addView(label("Capitán de esta jornada",14,true));Spinner captain=spinner(captains);selectById(captain,captains,saved.captain);form.addView(captain);
        form.addView(label("Publicador encargado de transportar los módulos al inicio y al final del día",14,true));Spinner transport=spinner(choices);selectById(transport,choices,saved.transport);form.addView(transport);
        AlertDialog dialog=safeDialog().setTitle("Capitán y transporte · "+day).setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(x->{PreachingCampaign.DayResponsibilities result=new PreachingCampaign.DayResponsibilities();result.captain=((Models.Student)captain.getSelectedItem()).id;result.transport=((Models.Student)transport.getSelectedItem()).id;
            try{repo.saveDayResponsibilities(c,day,result);dialog.dismiss();toast("Capitán y transporte guardados.");showPreachingRoster(c,day);}catch(Exception error){toast(error.getMessage());}}));dialog.show();
    }
    private void choosePreachingWeek(PreachingCampaign.Campaign c){
        LinearLayout form=column();form.setPadding(dp(14),dp(8),dp(14),dp(8));form.addView(label("Elija una fecha de la semana del reporte",14,true));EditText day=dateInput(c.startDate);form.addView(day);
        AlertDialog dialog=safeDialog().setTitle("Reporte semanal JPG · Todos los exhibidores").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Generar",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(x->{try{File image=new PreachingReportManager(this,db).weeklyJpg(c,LocalDate.parse(value(day)));dialog.dismiss();shareFile(image,"image/jpeg","Programa semanal de predicación · "+value(day),null);}catch(Exception error){toast("No se pudo generar la imagen: "+error.getMessage());}}));dialog.show();
    }
    private void choosePreachingShift(PreachingCampaign.Campaign c){
        LinearLayout form=column();form.setPadding(dp(14),dp(8),dp(14),dp(8));form.addView(label("Fecha",14,true));EditText day=dateInput(c.startDate);form.addView(day);
        List<String> shiftLabels=new ArrayList<>(),exhibitorLabels=new ArrayList<>();for(int i=0;i<c.slots();i++)shiftLabels.add(c.slotLabel(i));for(int i=1;i<=c.exhibitors;i++)exhibitorLabels.add("Exhibidor "+i);
        form.addView(label("Turno",14,true));Spinner shift=spinner(shiftLabels);form.addView(shift);form.addView(label("Exhibidor",14,true));Spinner exhibitor=spinner(exhibitorLabels);form.addView(exhibitor);
        AlertDialog dialog=safeDialog().setTitle("Imagen del turno · WhatsApp").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Generar",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(x->{try{File image=new PreachingReportManager(this,db).shiftJpg(c,value(day),shift.getSelectedItemPosition(),exhibitor.getSelectedItemPosition()+1);dialog.dismiss();shareFile(image,"image/jpeg","Turno de predicación el "+value(day)+" · "+shift.getSelectedItem(),"com.whatsapp.w4b");}catch(Exception error){toast("No se pudo generar la imagen: "+error.getMessage());}}));dialog.show();
    }

    private void showTransportHome(){
        ScrollView page=page("Programa de Transporte");LinearLayout body=body(page);TransportProgram repository=new TransportProgram(db);
        body.addView(label("Cree el viaje y sus autobuses, forme la lista global de pasajeros, cierre el registro y después asigne los asientos.",14,false));
        body.addView(button("Nuevo viaje",v->editTransportTrip(null)));
        body.addView(button("Personas interesadas",v->showInterestedPeople()));
        List<TransportProgram.Trip> trips=repository.trips();if(trips.isEmpty())body.addView(label("Aún no hay viajes registrados.",15,false));
        for(TransportProgram.Trip trip:trips){LinearLayout card=card();int passengers=repository.passengerCount(trip.id),capacity=repository.totalCapacity(trip.id),seated=repository.seatedCount(trip.id);card.addView(label(trip.date+" · "+trip.time+" · "+trip.reason,17,true));card.addView(label("Salida: "+trip.departurePlace+" · Autobuses: "+repository.buses(trip.id).size()+" · Pasajeros: "+passengers+"/"+capacity+" · Asientos: "+seated+"/"+passengers,13,false));card.addView(label(trip.finalized?"Programa finalizado":trip.registrationClosed||passengers>=capacity?"Registro cerrado · Asignación de asientos habilitada":"Registro de pasajeros abierto",13,true));card.addView(button("Abrir viaje",v->showTransportTrip(trip.id)));body.addView(card);}
    }
    private void editTransportTrip(TransportProgram.Trip current){
        LinearLayout form=column();form.setPadding(dp(14),dp(6),dp(14),dp(6));EditText date=dateInput(current==null?LocalDate.now().toString():current.date);EditText time=preachingTimeInput(current==null?"06:30":current.time);EditText reason=input("Motivo del viaje",current==null?"":current.reason);EditText departure=input("Lugar de salida",current==null?"":current.departurePlace);EditText congregation=input("Nombre de la congregación",current==null?"":current.congregation);EditText locality=input("Localidad",current==null?"":current.locality);form.addView(label("Fecha de viaje",14,true));form.addView(date);form.addView(label("Hora de salida",14,true));form.addView(time);form.addView(label("Motivo del viaje",14,true));form.addView(reason);form.addView(label("Lugar de salida",14,true));form.addView(departure);form.addView(label("Congregación para el encabezado",14,true));form.addView(congregation);form.addView(label("Localidad para el encabezado",14,true));form.addView(locality);EditText count=null;if(current==null){count=input("Cantidad de autobuses","1");count.setInputType(InputType.TYPE_CLASS_NUMBER);form.addView(label("Cantidad de autobuses",14,true));form.addView(count);}final EditText busCount=count;
        AlertDialog dialog=safeDialog().setTitle(current==null?"Nuevo viaje":"Editar datos del viaje").setView(form).setNegativeButton("Cancelar",null).setPositiveButton(current==null?"Continuar":"Guardar",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(x->{TransportProgram.Trip trip=current==null?new TransportProgram.Trip():current;trip.date=value(date);trip.time=value(time);trip.reason=value(reason);trip.departurePlace=value(departure);trip.congregation=value(congregation);trip.locality=value(locality);try{TransportProgram.validateTrip(trip);if(current==null){int buses=intValue(busCount,0);if(buses<1||buses>20){toast("Defina entre uno y veinte autobuses.");return;}dialog.dismiss();configureTransportBuses(trip,buses);}else{new TransportProgram(db).updateTrip(trip);dialog.dismiss();showTransportTrip(trip.id);}}catch(Exception error){toast(error.getMessage());}}));dialog.show();
    }
    private void configureTransportBuses(TransportProgram.Trip trip,int count){
        LinearLayout fields=column();fields.setPadding(dp(14),dp(4),dp(14),dp(4));EditText[] capacities=new EditText[count];for(int i=0;i<count;i++){fields.addView(label("Capacidad del autobús "+(i+1),14,true));capacities[i]=input("Asientos","41");capacities[i].setInputType(InputType.TYPE_CLASS_NUMBER);fields.addView(capacities[i]);}ScrollView scroll=new ScrollView(this);scroll.addView(fields);AlertDialog dialog=safeDialog().setTitle("Capacidad de cada autobús").setView(scroll).setNegativeButton("Cancelar",null).setPositiveButton("Crear viaje",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(x->{int[] values=new int[count];for(int i=0;i<count;i++)values[i]=intValue(capacities[i],0);try{long id=new TransportProgram(db).createTrip(trip,values);dialog.dismiss();toast("Viaje creado. Registre ahora la lista global de pasajeros.");showTransportTrip(id);}catch(Exception error){toast(error.getMessage());}}));dialog.show();
    }
    private void editTransportBuses(TransportProgram.Trip trip){
        TransportProgram repository=new TransportProgram(db);List<TransportProgram.Bus> current=repository.buses(trip.id);EditText count=input("Cantidad de autobuses",String.valueOf(current.size()));count.setInputType(InputType.TYPE_CLASS_NUMBER);AlertDialog first=safeDialog().setTitle("Cambiar autobuses y capacidades").setView(count).setNegativeButton("Cancelar",null).setPositiveButton("Continuar",null).create();first.setOnShowListener(v->first.getButton(-1).setOnClickListener(x->{int requested=intValue(count,0);if(requested<1||requested>20){toast("Defina entre uno y veinte autobuses.");return;}first.dismiss();editTransportBusCapacities(trip,current,requested);}));first.show();
    }
    private void editTransportBusCapacities(TransportProgram.Trip trip,List<TransportProgram.Bus> current,int count){
        LinearLayout fields=column();fields.setPadding(dp(14),dp(4),dp(14),dp(4));EditText[] capacities=new EditText[count];for(int i=0;i<count;i++){fields.addView(label("Capacidad del autobús "+(i+1),14,true));capacities[i]=input("Asientos",i<current.size()?String.valueOf(current.get(i).capacity):"41");capacities[i].setInputType(InputType.TYPE_CLASS_NUMBER);fields.addView(capacities[i]);}ScrollView scroll=new ScrollView(this);scroll.addView(fields);AlertDialog dialog=safeDialog().setTitle("Capacidad de cada autobús").setView(scroll).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(x->{int[] values=new int[count];for(int i=0;i<count;i++)values[i]=intValue(capacities[i],0);try{new TransportProgram(db).replaceBuses(trip,values);dialog.dismiss();toast("Autobuses y capacidades actualizados.");showTransportTrip(trip.id);}catch(Exception error){toast(error.getMessage());}}));dialog.show();
    }
    private void showTransportTrip(long tripId){
        TransportProgram repository=new TransportProgram(db);TransportProgram.Trip trip;try{trip=repository.trip(tripId);}catch(Exception error){toast(error.getMessage());showTransportHome();return;}ScrollView page=page("Viaje · "+trip.date);LinearLayout body=body(page);int passengers=repository.passengerCount(trip.id),capacity=repository.totalCapacity(trip.id),seated=repository.seatedCount(trip.id);body.addView(label(trip.reason,20,true));body.addView(label("Salida: "+trip.date+" a las "+trip.time+" · "+trip.departurePlace,15,false));if(!trip.congregation.isEmpty()||!trip.locality.isEmpty())body.addView(label("Congregación: "+trip.congregation+(trip.locality.isEmpty()?"":" · "+trip.locality),14,false));body.addView(label("Pasajeros "+passengers+"/"+capacity+" · Asientos asignados "+seated+"/"+passengers+" · "+(trip.finalized?"Finalizado":trip.registrationClosed||passengers>=capacity?"Registro cerrado":"Registro abierto"),14,true));body.addView(button("Editar datos y encabezado del viaje",v->editTransportTrip(trip)));if(passengers==0&&!trip.finalized)body.addView(button("Cambiar autobuses y capacidades",v->editTransportBuses(trip)));body.addView(button("Lista global de pasajeros",v->showTransportPassengers(trip.id)));if(repository.canAssignSeats(trip))body.addView(button("Asignar asientos, capitanes y auxiliares",v->showTransportSeats(trip.id)));if(trip.finalized){body.addView(button("PDF final para impresión",v->shareTransportPdf(trip)));body.addView(button("JPG de un autobús para WhatsApp",v->shareTransportJpg(trip)));body.addView(button("Reabrir asignación de asientos",v->safeDialog().setTitle("¿Reabrir el programa?").setMessage("Podrá modificar asientos, capitanes y auxiliares. Después deberá finalizarlo nuevamente.").setNegativeButton("Cancelar",null).setPositiveButton("Reabrir",(d,w)->{repository.reopenProgram(trip);showTransportTrip(trip.id);}).show()));}
        body.addView(button("Ver histórico de este viaje",v->showTransportTripHistory(trip.id)));body.addView(button("Eliminar viaje",v->safeDialog().setTitle("¿Eliminar definitivamente el viaje?").setMessage("Se eliminarán sus autobuses, pasajeros y asientos. Esta acción no puede deshacerse.").setNegativeButton("Cancelar",null).setPositiveButton("Eliminar",(d,w)->{repository.deleteTrip(trip.id);showTransportHome();}).show()));body.addView(button("Volver",v->showTransportHome()));
    }
    private void showTransportPassengers(long tripId){
        TransportProgram repository=new TransportProgram(db);TransportProgram.Trip trip=repository.trip(tripId);ScrollView page=page("Lista global de pasajeros");LinearLayout body=body(page);int count=repository.passengerCount(trip.id),capacity=repository.totalCapacity(trip.id);body.addView(label(trip.reason+" · "+count+"/"+capacity+" lugares",17,true));body.addView(label(trip.registrationClosed||count>=capacity?"Registro cerrado. Ya puede asignar asientos.":"Registro abierto.",14,true));
        if(!trip.registrationClosed&&count<capacity){body.addView(button("Agregar alumno o persona registrada",v->addRegisteredTransportPassenger(trip)));body.addView(button("Agregar nueva persona interesada",v->addQuickInterestedPassenger(trip)));body.addView(button("Terminar registro y asignar asientos",v->safeDialog().setTitle("¿Cerrar el registro?").setMessage("Podrá comenzar a asignar los asientos aunque queden lugares disponibles.").setNegativeButton("Cancelar",null).setPositiveButton("Cerrar registro",(d,w)->{repository.closeRegistration(trip);showTransportPassengers(trip.id);}).show()));}else if(!trip.finalized&&count<capacity)body.addView(button("Reabrir registro",v->{try{repository.reopenRegistration(trip);showTransportPassengers(trip.id);}catch(Exception error){toast(error.getMessage());}}));
        for(TransportProgram.Passenger passenger:repository.passengers(trip.id)){LinearLayout card=card();card.addView(label(passenger.name,15,true));card.addView(label(passenger.hasSeat()?"Autobús "+repository.bus(passenger.busId).number+" · Asiento "+passenger.seatNumber:"Sin asiento asignado",13,false));if(!trip.finalized)card.addView(button("Quitar de la lista",v->safeDialog().setTitle("¿Quitar a "+passenger.name+"?").setNegativeButton("Cancelar",null).setPositiveButton("Quitar",(d,w)->{try{repository.removePassenger(trip,passenger.id);showTransportPassengers(trip.id);}catch(Exception error){toast(error.getMessage());}}).show()));body.addView(card);}
        if(repository.canAssignSeats(trip))body.addView(button("Continuar a asignación de asientos",v->showTransportSeats(trip.id)));body.addView(button("Volver al viaje",v->showTransportTrip(trip.id)));
    }
    private void addRegisteredTransportPassenger(TransportProgram.Trip trip){TransportProgram repository=new TransportProgram(db);List<TransportProgram.PersonChoice> choices=repository.availablePeople(trip.id);if(choices.isEmpty()){toast("No hay personas disponibles para agregar.");return;}Spinner spinner=spinner(choices);safeDialog().setTitle("Agregar pasajero").setView(spinner).setNegativeButton("Cancelar",null).setPositiveButton("Agregar",(d,w)->{try{repository.registerPassenger(trip,(TransportProgram.PersonChoice)spinner.getSelectedItem());showTransportPassengers(trip.id);}catch(Exception error){toast(error.getMessage());}}).show();}
    private void addQuickInterestedPassenger(TransportProgram.Trip trip){EditText name=input("Nombre completo","");AlertDialog dialog=safeDialog().setTitle("Nueva persona interesada").setView(name).setNegativeButton("Cancelar",null).setPositiveButton("Agregar al viaje",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(x->{try{TransportProgram repository=new TransportProgram(db);TransportProgram.InterestedPerson person=repository.addInterestedPerson(value(name));repository.registerPassenger(trip,new TransportProgram.PersonChoice(TransportProgram.INTERESTED,person.id,person.name));dialog.dismiss();showTransportPassengers(trip.id);}catch(Exception error){toast(error.getMessage());}}));dialog.show();}
    private void showInterestedPeople(){ScrollView page=page("Personas interesadas");LinearLayout body=body(page);TransportProgram repository=new TransportProgram(db);body.addView(button("Agregar persona",v->editInterestedPerson(null)));for(TransportProgram.InterestedPerson person:repository.interestedPeople()){LinearLayout card=card();card.addView(label(person.name,15,true));card.addView(button("Editar",v->editInterestedPerson(person)));card.addView(button("Eliminar",v->safeDialog().setTitle("¿Eliminar a "+person.name+"?").setNegativeButton("Cancelar",null).setPositiveButton("Eliminar",(d,w)->{try{repository.deleteInterestedPerson(person);showInterestedPeople();}catch(Exception error){toast(error.getMessage());}}).show()));body.addView(card);}body.addView(button("Volver",v->showTransportHome()));}
    private void editInterestedPerson(TransportProgram.InterestedPerson person){EditText name=input("Nombre",person==null?"":person.name);AlertDialog dialog=safeDialog().setTitle(person==null?"Nueva persona interesada":"Editar persona interesada").setView(name).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(x->{try{TransportProgram repository=new TransportProgram(db);if(person==null)repository.addInterestedPerson(value(name));else repository.renameInterestedPerson(person,value(name));dialog.dismiss();showInterestedPeople();}catch(Exception error){toast(error.getMessage());}}));dialog.show();}
    private void showTransportSeats(long tripId){
        TransportProgram repository=new TransportProgram(db);TransportProgram.Trip trip=repository.trip(tripId);if(!repository.canAssignSeats(trip)){toast("Cierre primero el registro de pasajeros.");return;}ScrollView page=page("Asignación de asientos");LinearLayout body=body(page);body.addView(label(trip.reason+" · "+trip.date,18,true));body.addView(label("Toque un asiento para asignar o cambiar su pasajero. El capitán y el auxiliar deben ser personas distintas y tener asiento en el mismo autobús.",14,false));
        for(TransportProgram.Bus bus:repository.buses(trip.id)){LinearLayout card=card();card.addView(label("AUTOBÚS No. "+bus.number+" · Capacidad "+bus.capacity,17,true));card.addView(label("Capitán: "+(bus.captainPassengerId==0?"PENDIENTE":repository.passengerName(bus.captainPassengerId))+"\nAuxiliar: "+(bus.assistantPassengerId==0?"PENDIENTE":repository.passengerName(bus.assistantPassengerId)),14,true));HorizontalScrollView horizontal=new HorizontalScrollView(this);GridLayout grid=new GridLayout(this);grid.setColumnCount(4);for(int seat=1;seat<=bus.capacity;seat++){final int selectedSeat=seat;TransportProgram.Passenger passenger=repository.passengerAt(bus.id,seat);Button button=button(seat+"\n"+(passenger==null?"Disponible":passenger.name),v->editTransportSeat(trip,bus,selectedSeat));button.setAllCaps(false);GridLayout.LayoutParams params=new GridLayout.LayoutParams();params.width=dp(155);params.height=dp(76);params.setMargins(dp(2),dp(2),dp(2),dp(2));grid.addView(button,params);}horizontal.addView(grid);card.addView(horizontal);card.addView(button("Elegir capitán del autobús",v->chooseTransportCaptain(trip,bus)));card.addView(button("Elegir auxiliar del capitán",v->chooseTransportAssistant(trip,bus)));body.addView(card);}
        if(!trip.finalized)body.addView(button("Finalizar programa de transporte",v->{try{repository.finalizeProgram(trip);toast("Programa finalizado. Ya puede generar PDF y JPG.");showTransportTrip(trip.id);}catch(Exception error){toast(error.getMessage());}}));body.addView(button("Volver al viaje",v->showTransportTrip(trip.id)));
    }
    private void editTransportSeat(TransportProgram.Trip trip,TransportProgram.Bus bus,int seat){TransportProgram repository=new TransportProgram(db);TransportProgram.Passenger current=repository.passengerAt(bus.id,seat);List<TransportProgram.Passenger> choices=new ArrayList<>();if(current!=null)choices.add(current);for(TransportProgram.Passenger passenger:repository.passengers(trip.id))if(!passenger.hasSeat())choices.add(passenger);if(choices.isEmpty()){toast("No hay pasajeros pendientes de asiento.");return;}Spinner spinner=spinner(choices);AlertDialog.Builder builder=safeDialog().setTitle("Autobús "+bus.number+" · Asiento "+seat).setView(spinner).setNegativeButton("Cancelar",null).setPositiveButton("Asignar",(d,w)->{try{repository.assignSeat(trip,bus,seat,((TransportProgram.Passenger)spinner.getSelectedItem()).id);showTransportSeats(trip.id);}catch(Exception error){toast(error.getMessage());}});if(current!=null)builder.setNeutralButton("Liberar asiento",(d,w)->{try{repository.clearSeat(trip,current.id);showTransportSeats(trip.id);}catch(Exception error){toast(error.getMessage());}});builder.show();}
    private void chooseTransportCaptain(TransportProgram.Trip trip,TransportProgram.Bus bus){TransportProgram repository=new TransportProgram(db);List<TransportProgram.Passenger> passengers=repository.passengersOnBus(bus.id);if(passengers.isEmpty()){toast("Asigne primero pasajeros a este autobús.");return;}Spinner spinner=spinner(passengers);for(int i=0;i<passengers.size();i++)if(passengers.get(i).id==bus.captainPassengerId)spinner.setSelection(i);safeDialog().setTitle("Capitán del autobús "+bus.number).setView(spinner).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",(d,w)->{try{repository.setCaptain(trip,bus,((TransportProgram.Passenger)spinner.getSelectedItem()).id);showTransportSeats(trip.id);}catch(Exception error){toast(error.getMessage());}}).show();}
    private void chooseTransportAssistant(TransportProgram.Trip trip,TransportProgram.Bus bus){TransportProgram repository=new TransportProgram(db);List<TransportProgram.Passenger> passengers=repository.passengersOnBus(bus.id);if(passengers.size()<2){toast("El autobús necesita al menos dos pasajeros con asiento para elegir capitán y auxiliar.");return;}Spinner spinner=spinner(passengers);for(int i=0;i<passengers.size();i++)if(passengers.get(i).id==bus.assistantPassengerId)spinner.setSelection(i);safeDialog().setTitle("Auxiliar del capitán · Autobús "+bus.number).setView(spinner).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",(d,w)->{try{repository.setAssistant(trip,bus,((TransportProgram.Passenger)spinner.getSelectedItem()).id);showTransportSeats(trip.id);}catch(Exception error){toast(error.getMessage());}}).show();}
    private void shareTransportPdf(TransportProgram.Trip trip){try{File file=new TransportReportManager(this,db).pdf(trip);shareFile(file,"application/pdf","Programa de transporte · "+trip.reason,null);}catch(Exception error){toast("No fue posible generar el PDF: "+error.getMessage());}}
    private void shareTransportJpg(TransportProgram.Trip trip){TransportProgram repository=new TransportProgram(db);List<TransportProgram.Bus> buses=repository.buses(trip.id);Spinner spinner=spinner(buses);safeDialog().setTitle("Autobús para la imagen").setView(spinner).setNegativeButton("Cancelar",null).setPositiveButton("Generar",(d,w)->{TransportProgram.Bus bus=(TransportProgram.Bus)spinner.getSelectedItem();try{File file=new TransportReportManager(this,db).jpg(trip,bus);shareFile(file,"image/jpeg","Programa de transporte · Autobús "+bus.number,"com.whatsapp.w4b");}catch(Exception error){toast("No fue posible generar la imagen: "+error.getMessage());}}).show();}
    private void showTransportHistory(){ScrollView page=page("Historial de Transporte");LinearLayout body=body(page);TransportProgram repository=new TransportProgram(db);body.addView(label("Viajes y sus pasajeros",17,true));for(TransportProgram.Trip trip:repository.trips())body.addView(button(trip.toString(),v->showTransportTripHistory(trip.id)));body.addView(divider());body.addView(label("Pasajeros y sus viajes",17,true));List<TransportProgram.PersonChoice> people=repository.historicalPeople();if(people.isEmpty())body.addView(label("No hay pasajeros con viajes registrados.",14,false));else{Spinner person=spinner(people);LinearLayout results=column();body.addView(person);body.addView(button("Consultar pasajero",v->renderPassengerTrips((TransportProgram.PersonChoice)person.getSelectedItem(),results)));body.addView(results);renderPassengerTrips(people.get(0),results);}body.addView(button("Volver a Transportes",v->showTransportHome()));}
    private void showTransportTripHistory(long tripId){TransportProgram repository=new TransportProgram(db);TransportProgram.Trip trip=repository.trip(tripId);ScrollView page=page("Histórico del viaje");LinearLayout body=body(page);body.addView(label(trip.reason,19,true));body.addView(label(trip.date+" · "+trip.time+" · Salida: "+trip.departurePlace,14,false));for(TransportProgram.Bus bus:repository.buses(trip.id)){body.addView(label("Autobús "+bus.number+" · Capitán: "+(bus.captainPassengerId==0?"Pendiente":repository.passengerName(bus.captainPassengerId))+" · Auxiliar: "+(bus.assistantPassengerId==0?"Pendiente":repository.passengerName(bus.assistantPassengerId)),16,true));for(TransportProgram.Passenger passenger:repository.passengersOnBus(bus.id))body.addView(label("Asiento "+passenger.seatNumber+" · "+passenger.name+(passenger.id==bus.captainPassengerId?" · CAPITÁN":passenger.id==bus.assistantPassengerId?" · AUXILIAR":""),13,false));}List<TransportProgram.Passenger> waiting=new ArrayList<>();for(TransportProgram.Passenger passenger:repository.passengers(trip.id))if(!passenger.hasSeat())waiting.add(passenger);if(!waiting.isEmpty()){body.addView(label("Sin asiento",15,true));for(TransportProgram.Passenger passenger:waiting)body.addView(label(passenger.name,13,false));}body.addView(button("Volver al historial",v->showTransportHistory()));}
    private void renderPassengerTrips(TransportProgram.PersonChoice person,LinearLayout results){results.removeAllViews();List<TransportProgram.PassengerTrip> trips=new TransportProgram(db).tripsFor(person.kind,person.sourceId);results.addView(label(person.name+" · "+trips.size()+" viaje(s)",16,true));for(TransportProgram.PassengerTrip trip:trips){LinearLayout card=card();card.addView(label(trip.date+" · "+trip.time+" · "+trip.reason,14,true));card.addView(label("Salida: "+trip.departurePlace+" · "+(trip.bus==0?"Sin asiento":"Autobús "+trip.bus+", asiento "+trip.seat)+(trip.captain?" · CAPITÁN":trip.assistant?" · AUXILIAR":""),13,false));results.addView(card);}}
    private void styleColorButton(Button button,String hex){
        int chosen=Color.parseColor(hex);GradientDrawable background=new GradientDrawable();background.setColor(chosen);background.setCornerRadius(dp(7));background.setStroke(dp(1),Color.DKGRAY);button.setBackground(background);button.setText(hex);double light=(Color.red(chosen)*299+Color.green(chosen)*587+Color.blue(chosen)*114)/1000.0;button.setTextColor(light>145?Color.BLACK:Color.WHITE);
    }
    private void openColorPalette(String title,Map<String,String> choices,String key,Button preview){
        String[] swatches={"#000000","#374151","#6B7280","#BFBFBF","#FFFFFF","#7F1D1D","#B91C1C","#EF4444","#F97316","#ED7D31","#92400E","#FFC000","#FDE047","#166534","#00B050","#22C55E","#0F766E","#06B6D4","#1E3A8A","#0D6EFD","#60A5FA","#581C87","#955FEF","#D946EF"};
        GridLayout grid=new GridLayout(this);grid.setColumnCount(4);grid.setPadding(dp(12),dp(12),dp(12),dp(12));AlertDialog dialog=safeDialog().setTitle("Elegir color: "+title).setView(grid).setNegativeButton("Cancelar",null).create();
        for(String hex:swatches){Button swatch=new Button(this);swatch.setContentDescription(hex);swatch.setMinWidth(0);swatch.setMinimumWidth(0);swatch.setMinHeight(0);swatch.setMinimumHeight(0);styleColorButton(swatch,hex);GridLayout.LayoutParams params=new GridLayout.LayoutParams();params.width=dp(62);params.height=dp(52);params.setMargins(dp(4),dp(4),dp(4),dp(4));swatch.setLayoutParams(params);swatch.setOnClickListener(v->{choices.put(key,hex);styleColorButton(preview,hex);dialog.dismiss();});grid.addView(swatch);}dialog.show();
    }

    private void showStudents(){
        ScrollView page=page("Catálogo de alumnos");LinearLayout body=body(page);addExcelTools(body);body.addView(button("+ Agregar alumno",v->editStudent(null)));
        List<Models.Student> students=db.students(true);if(students.isEmpty())body.addView(label("Aún no hay alumnos registrados.",16,false));
        for(Models.Student s:students){LinearLayout c=card();c.addView(label(s.name,17,true));c.addView(label("Celular: "+(s.phone.isEmpty()?"—":s.phone)+"  ·  Sexo: "+s.sex+"  ·  Calificador: "+s.rating+(s.isSuspended()?" (Suspendido)":""),14,false));c.addView(label((s.baptized?"Bautizado · ":"")+(s.appointed?"Nombrado · ":"")+(s.pioneer?"Precursor":""),13,false));c.addView(label("Grupo: "+(s.groupNumber==0?"Sin asignar":s.groupNumber),13,true));c.addView(label(studentClassifications(s),12,false));LinearLayout actions=row();actions.addView(button("Editar",v->editStudent(s)));actions.addView(button("Eliminar",v->confirm("Eliminar a "+s.name+"?",()->{db.deleteStudent(s.id);showStudents();})));c.addView(actions);body.addView(c);}
    }

    private String studentClassifications(Models.Student s){List<String> labels=new ArrayList<>();if(s.vidaMinisterio)labels.add("Vida y Ministerio");if(s.discursoPublico)labels.add("Discurso Público");if(s.atalaya)labels.add("Atalaya");if(s.lectorAtalaya)labels.add("Lector de la Atalaya");if(s.acomodadorEntrada)labels.add("Acomodador de Entrada");if(s.acomodadorAuditorio1)labels.add("Acomodador de Auditorio 1");if(s.acomodadorAuditorio2)labels.add("Acomodador de Auditorio 2");if(s.microfono1)labels.add("Micrófono 1");if(s.microfono2)labels.add("Micrófono 2");if(s.plataforma1)labels.add("Plataforma 1");if(s.plataforma2)labels.add("Plataforma 2");if(s.oracionInicial)labels.add("Oración inicial");if(s.oracionFinal)labels.add("Oración final");if(s.estudioLibro)labels.add("Estudio del Libro");if(s.lectorEstudioLibro)labels.add("Lector del Estudio del Libro");return labels.isEmpty()?"Sin clasificaciones adicionales":String.join(" · ",labels);}

    private void editStudent(Models.Student current){
        Models.Student s=current==null?new Models.Student():current;LinearLayout form=column();form.setPadding(dp(16),0,dp(16),0);
        EditText name=input("Nombre",current==null?"":s.name),phone=input("Celular",current==null?"":s.phone),rating=input("Calificador 0 a 10",current==null?"":String.valueOf(s.rating));rating.setInputType(InputType.TYPE_CLASS_NUMBER);Spinner sex=spinner(Arrays.asList("Sexo","Hombre","Mujer"));if(current!=null&&!s.sex.trim().isEmpty())sex.setSelection(s.isFemale()?2:1);
        EditText group=input("Grupo",current==null||s.groupNumber==0?"":String.valueOf(s.groupNumber));group.setInputType(InputType.TYPE_CLASS_NUMBER);
        CheckBox baptized=new CheckBox(this);baptized.setText("Bautizado");baptized.setChecked(s.baptized);CheckBox appointed=new CheckBox(this);appointed.setText("Nombrado");appointed.setChecked(s.appointed);CheckBox pioneer=new CheckBox(this);pioneer.setText("Precursor");pioneer.setChecked(s.pioneer);
        CheckBox vidaMinisterio=checkBox("Vida y Ministerio",s.vidaMinisterio),discursoPublico=checkBox("Discurso Público",s.discursoPublico),atalaya=checkBox("Atalaya",s.atalaya),lectorAtalaya=checkBox("Lector de la Atalaya",s.lectorAtalaya),acomodadorEntrada=checkBox("Acomodador de Entrada",s.acomodadorEntrada),acomodadorAuditorio1=checkBox("Acomodador de Auditorio 1",s.acomodadorAuditorio1),acomodadorAuditorio2=checkBox("Acomodador de Auditorio 2",s.acomodadorAuditorio2),microfono1=checkBox("Micrófono 1",s.microfono1),microfono2=checkBox("Micrófono 2",s.microfono2),plataforma1=checkBox("Plataforma 1",s.plataforma1),plataforma2=checkBox("Plataforma 2",s.plataforma2),oracionInicial=checkBox("Oración inicial",s.oracionInicial),oracionFinal=checkBox("Oración final",s.oracionFinal),estudioLibro=checkBox("Estudio del Libro",s.estudioLibro),lectorEstudioLibro=checkBox("Lector del Estudio del Libro",s.lectorEstudioLibro);
        form.addView(label("Datos generales",16,true));form.addView(name);form.addView(phone);form.addView(sex);form.addView(rating);form.addView(group);form.addView(baptized);form.addView(appointed);form.addView(pioneer);form.addView(label("Clasificación para los módulos",16,true));form.addView(vidaMinisterio);form.addView(discursoPublico);form.addView(atalaya);form.addView(lectorAtalaya);form.addView(acomodadorEntrada);form.addView(acomodadorAuditorio1);form.addView(acomodadorAuditorio2);form.addView(microfono1);form.addView(microfono2);form.addView(plataforma1);form.addView(plataforma2);form.addView(oracionInicial);form.addView(oracionFinal);form.addView(estudioLibro);form.addView(lectorEstudioLibro);
        ScrollView sc=new ScrollView(this);sc.addView(form);AlertDialog dialog=safeDialog().setTitle(current==null?"Nuevo alumno":"Editar alumno").setView(sc).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{int r=intValue(rating,-1),g=value(group).isEmpty()?0:intValue(group,-1);if(value(name).isEmpty()||r<0||r>10||sex.getSelectedItemPosition()==0){toast("Indique nombre, sexo y un calificador de 0 a 10.");return;}if(g<0){toast("Grupo debe ser un número igual o mayor que 0.");return;}s.name=value(name);s.phone=value(phone);s.sex=String.valueOf(sex.getSelectedItem());s.rating=r;s.groupNumber=g;s.baptized=baptized.isChecked();s.appointed=appointed.isChecked();s.pioneer=pioneer.isChecked();s.vidaMinisterio=vidaMinisterio.isChecked();s.discursoPublico=discursoPublico.isChecked();s.atalaya=atalaya.isChecked();s.lectorAtalaya=lectorAtalaya.isChecked();s.acomodadorEntrada=acomodadorEntrada.isChecked();s.acomodadorAuditorio1=acomodadorAuditorio1.isChecked();s.acomodadorAuditorio2=acomodadorAuditorio2.isChecked();s.microfono1=microfono1.isChecked();s.microfono2=microfono2.isChecked();s.plataforma1=plataforma1.isChecked();s.plataforma2=plataforma2.isChecked();s.oracionInicial=oracionInicial.isChecked();s.oracionFinal=oracionFinal.isChecked();s.estudioLibro=estudioLibro.isChecked();s.lectorEstudioLibro=lectorEstudioLibro.isChecked();db.saveStudent(s);dialog.dismiss();showStudents();}));dialog.show();
    }

    private CheckBox checkBox(String text,boolean checked){CheckBox box=new CheckBox(this);box.setText(text);box.setChecked(checked);return box;}

    private void showPresidents(){
        ScrollView page=page("Catálogo independiente de presidentes");LinearLayout body=body(page);addExcelTools(body);body.addView(button("+ Agregar presidente",v->editPresident(null)));
        for(Models.President p:db.presidents()){LinearLayout c=card();c.addView(label(p.name,17,true));c.addView(label("Celular: "+(p.phone.isEmpty()?"—":p.phone),14,false));LinearLayout a=row();a.addView(button("Editar",v->editPresident(p)));a.addView(button("Eliminar",v->confirm("Eliminar a "+p.name+"?",()->{db.deletePresident(p.id);showPresidents();})));c.addView(a);body.addView(c);}
    }

    private void editPresident(Models.President current){Models.President p=current==null?new Models.President():current;LinearLayout form=column();EditText name=input("Nombre",p.name),phone=input("Celular",p.phone);form.addView(name);form.addView(phone);AlertDialog d=safeDialog().setTitle(current==null?"Nuevo presidente":"Editar presidente").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{if(value(name).isEmpty()){toast("Indique el nombre.");return;}p.name=value(name);p.phone=value(phone);db.savePresident(p);d.dismiss();showPresidents();}));d.show();}

    private void showPresentations(){
        ScrollView page=page("Catálogo de presentaciones");LinearLayout body=body(page);LinearLayout filters=row();
        EditText month=input("AAAA-MM",selectedMonth);filters.addView(month,new LinearLayout.LayoutParams(0,-2,1));filters.addView(button("Ver",v->{if(value(month).matches("\\d{4}-\\d{2}")){selectedMonth=value(month);showPresentations();}}));body.addView(filters);addExcelTools(body);body.addView(button("+ Agregar presentación",v->editPresentation(null)));
        List<Models.Presentation> list=db.presentations(selectedMonth);if(list.isEmpty())body.addView(label("No hay presentaciones en "+selectedMonth+".",16,false));
        for(Models.Presentation p:list){LinearLayout c=card();c.addView(label(p.date+" · "+p.number+". "+p.assignmentName+" ("+p.minutes+" min.)",16,true));c.addView(label("Marco: "+p.framework+"\nFuente de información: "+presentationSource(p)+"\nTema: "+p.topic+"\nLección: "+p.lesson,13,false));LinearLayout a=row();a.addView(button("Editar",v->editPresentation(p)));a.addView(button("Eliminar",v->confirm("Eliminar esta presentación y sus asignaciones?",()->{db.deletePresentation(p.id);showPresentations();})));c.addView(a);body.addView(c);}
    }

    private void editPresentation(Models.Presentation current){
        Models.Presentation p=current==null?new Models.Presentation():current;LinearLayout form=column();form.setPadding(dp(12),0,dp(12),0);
        EditText date=dateInput(current==null?activeWeek:p.date);EditText number=input("Número de intervención",current==null?"":String.valueOf(p.number));number.setInputType(InputType.TYPE_CLASS_NUMBER);EditText name=input("Nombre de la asignación",current==null?"":p.assignmentName);EditText minutes=input("Tiempo en minutos",current==null?"":String.valueOf(p.minutes));minutes.setInputType(InputType.TYPE_CLASS_NUMBER);EditText framework=input("Marco",current==null?"":p.framework);EditText source=input("Fuente de información",current==null?"":presentationSource(p));EditText topic=input("Tema",current==null?"":p.topic);EditText lesson=input("Lección",current==null?"":p.lesson);
        form.addView(date);form.addView(number);form.addView(name);form.addView(minutes);form.addView(framework);form.addView(source);form.addView(topic);form.addView(lesson);
        ScrollView sc=new ScrollView(this);sc.addView(form);AlertDialog d=safeDialog().setTitle(current==null?"Nueva presentación":"Editar presentación").setView(sc).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();
        d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{try{LocalDate.parse(value(date));}catch(Exception e){toast("Use una fecha válida AAAA-MM-DD.");return;}if(value(name).isEmpty()||intValue(number,0)<1||intValue(minutes,0)<1){toast("Complete nombre, número y tiempo.");return;}p.date=value(date);p.number=intValue(number,1);p.assignmentName=value(name);p.minutes=intValue(minutes,4);p.material="";p.framework=value(framework);p.source=value(source);p.topic=value(topic);p.lesson=value(lesson);db.savePresentation(p);selectedMonth=p.date.substring(0,7);d.dismiss();showPresentations();}));d.show();
    }

    private void showProgram(){
        ScrollView page=page("Programa mensual");LinearLayout body=body(page);
        body.addView(label("Mes del programa",14,true));Button monthButton=button(selectedMonthLabel(),v->openMonthPicker(()->{showActiveWeekOnly=false;appState.edit().putBoolean("show_active_week_only",false).apply();showProgram();}));monthButton.setAllCaps(false);monthButton.setCompoundDrawablesWithIntrinsicBounds(0,0,android.R.drawable.ic_menu_my_calendar,0);monthButton.setCompoundDrawablePadding(dp(8));body.addView(monthButton);
        body.addView(label("Semana activa: "+(activeWeek.isEmpty()?"sin seleccionar":activeWeek)+(showActiveWeekOnly?" · mostrando solo esta semana":""),14,true));if(showActiveWeekOnly)body.addView(button("Ver mes completo",v->{showActiveWeekOnly=false;appState.edit().putBoolean("show_active_week_only",false).apply();showProgram();}));
        HorizontalScrollView toolsScroll=new HorizontalScrollView(this);LinearLayout tools=row();tools.addView(button("Configurar semana",v->editMeeting()));tools.addView(button("Eliminar semana",v->selectWeekToDelete()));tools.addView(button("Evaluación",v->showEvaluation(activeWeek,0)));tools.addView(button("Nueva presentación",v->editPresentation(null)));tools.addView(button("Importar Excel",v->openExcelImporter()));tools.addView(button("Importar histórico",v->openHistoricalImporter()));tools.addView(button("Revisar duplicados",v->reviewDuplicates()));tools.addView(button("Plantilla Excel",v->shareExcelTemplate()));tools.addView(button("PDF mensual",v->createMonthlyPdf()));tools.addView(button("PDF semanal por sala",v->selectWeeklyRoomPdf()));tools.addView(button("4 asignaciones/hoja",v->createAssignmentsPdf()));tools.addView(button("Programar avisos",v->scheduleAllReminders()));toolsScroll.addView(tools);body.addView(toolsScroll);
        List<Models.Presentation> presentations=db.presentations(selectedMonth);List<Models.ProgramRow> rows=db.program(selectedMonth);if(showActiveWeekOnly&&!activeWeek.isEmpty()){presentations.removeIf(p->!activeWeek.equals(p.date));rows.removeIf(r->!activeWeek.equals(r.presentation.date));}
        if(presentations.isEmpty()){body.addView(label(showActiveWeekOnly?"No hay presentaciones capturadas para la semana "+activeWeek+".":"Cree las presentaciones del mes y después asigne alumnos manualmente.",16,false));return;}
        String previous="";
        for(Models.Presentation p:presentations){
            if(!p.date.equals(previous)){Models.Meeting meeting=db.meeting(p.date);Models.President president=db.president(meeting.presidentId);TextView date=label(p.date+"  ·  Presidente: "+(president==null?"Pendiente":president.name)+(meeting.suspended?"  ·  ASAMBLEA / SUSPENDIDA":"")+(meeting.visit?"  ·  VISITA: solo Sala Principal":""),16,true);date.setTextColor(Color.WHITE);date.setBackgroundColor(palette("date","#0D6EFD"));date.setPadding(dp(10),dp(8),dp(10),dp(8));body.addView(date);previous=p.date;}
            Models.Meeting meeting=db.meeting(p.date);LinearLayout c=card();c.addView(label(p.number+". "+p.assignmentName+" ("+p.minutes+" min.)",17,true));c.addView(label(presentationSource(p)+(p.topic.isEmpty()?"":" · "+p.topic)+(p.lesson.isEmpty()?"":" · Lección: "+p.lesson),13,false));
            if(meeting.suspended)c.addView(label("Sin asignaciones: reunión suspendida.",14,true));else{
                addRoomLine(c,p,"PRINCIPAL",findRow(rows,p.id,"PRINCIPAL"),false);
                if(!meeting.visit)addRoomLine(c,p,"AUXILIAR",findRow(rows,p.id,"AUXILIAR"),false);
                else c.addView(label("Sala Auxiliar deshabilitada durante la visita.",13,false));
            }body.addView(c);
        }
    }

    private Models.ProgramRow findRow(List<Models.ProgramRow> rows,long presentationId,String room){for(Models.ProgramRow r:rows)if(r.presentation.id==presentationId&&room.equals(r.movement.room))return r;return null;}

    private void addRoomLine(LinearLayout parent,Models.Presentation p,String room,Models.ProgramRow assigned,boolean compact){
        LinearLayout line=column();line.setPadding(dp(4),dp(5),dp(4),dp(5));String names=assigned==null?"Pendiente":assigned.student.name+(assigned.helper==null?"":"  ↔  "+assigned.helper.name);line.addView(label("Sala "+(room.equals("PRINCIPAL")?"Principal":"Auxiliar")+": "+names,14,assigned!=null));LinearLayout actions=row();actions.addView(button(assigned==null?"Asignar":"Cambiar",v->editMovement(p,room,assigned)));if(assigned!=null){actions.addView(button("JPG / WhatsApp",v->createAndShareJpg(assigned)));actions.addView(button("Quitar",v->confirm("¿Quitar esta asignación?",()->{db.deleteMovement(assigned.movement.id);showProgram();})));actions.addView(button("Evaluación",v->showEvaluation(p.date,assigned.movement.id)));}line.addView(actions);parent.addView(line);parent.addView(divider());
    }

    private void showEvaluation(String requestedDate,long focusMovementId){
        String date=requestedDate;if(date==null||date.isEmpty())date=activeWeek.isEmpty()?LocalDate.now().toString():activeWeek;try{LocalDate.parse(date);}catch(Exception e){date=LocalDate.now().toString();}selectedMonth=date.substring(0,7);
        ScrollView page=page("Evaluación semanal");LinearLayout body=body(page);EditText selector=dateInput(date);String selectedDate=date;selector.setOnClickListener(v->openDatePicker(selector,()->showEvaluation(value(selector),0)));body.addView(label("Semana que se evaluará",14,true));body.addView(selector);
        List<Models.ProgramRow> rows=new ArrayList<>();for(Models.ProgramRow r:db.program(selectedDate.substring(0,7)))if(r.presentation.date.equals(selectedDate))rows.add(r);if(rows.isEmpty()){body.addView(label("No hay asignaciones programadas para "+selectedDate+".",16,false));return;}
        if(focusMovementId>0)rows.sort((a,b)->a.movement.id==focusMovementId?-1:b.movement.id==focusMovementId?1:Integer.compare(a.presentation.number,b.presentation.number));
        for(Models.ProgramRow r:rows){LinearLayout c=card();String room="PRINCIPAL".equals(r.movement.room)?"Principal":"Auxiliar";c.addView(label(r.presentation.number+". "+r.presentation.assignmentName+" · Sala "+room,16,true));c.addView(label("Estudiante: "+r.student.name+(r.helper==null?"":"\nAyudante: "+r.helper.name)+"\nTiempo asignado: "+r.presentation.minutes+" min. · Lección: "+(r.presentation.lesson.isEmpty()?"—":r.presentation.lesson),13,false));if(r.movement.evaluated)c.addView(label("Evaluada · Tiempo real: "+formatSeconds(r.movement.actualSeconds)+" · Lección: "+(r.movement.lessonFulfilled?"cumplida":"no cumplida"),13,true));c.addView(button(r.movement.evaluated?"Editar evaluación":"Evaluar",v->editEvaluation(r)));body.addView(c);}
    }

    private void editEvaluation(Models.ProgramRow row){
        Models.Movement m=row.movement;LinearLayout form=column();form.setPadding(dp(12),0,dp(12),dp(12));form.addView(label(row.presentation.number+". "+row.presentation.assignmentName+"\n"+row.student.name+" · Sala "+("PRINCIPAL".equals(m.room)?"Principal":"Auxiliar")+"\nTiempo asignado: "+row.presentation.minutes+" min.",15,true));
        TextView clock=label(formatSeconds(m.actualSeconds),30,true);clock.setGravity(Gravity.CENTER);form.addView(clock);LinearLayout timerActions=row();Button start=button("Iniciar",null),pause=button("Pausar",null),reset=button("Reiniciar",null);timerActions.addView(start);timerActions.addView(pause);timerActions.addView(reset);form.addView(timerActions);
        CheckBox studentPresented=new CheckBox(this);studentPresented.setText("El estudiante presentó la asignación");studentPresented.setChecked(!m.evaluated||m.studentPresented);EditText studentReason=input("Causa por la que no se presentó",m.studentAbsenceReason);List<Models.Student> replacements=new ArrayList<>();Models.Student none=new Models.Student();none.name="Sin sustituto";replacements.add(none);for(Models.Student s:db.students(false))if(s.id!=m.studentId&&(!row.presentation.isIndividualMaleAssignment()||!s.isFemale()))replacements.add(s);Spinner replacement=spinner(replacements);selectById(replacement,replacements,m.replacementStudentId);TextView replacementTitle=label("Quién presentó en su lugar",14,true);form.addView(studentPresented);form.addView(studentReason);form.addView(replacementTitle);form.addView(replacement);
        CheckBox helperPresented=new CheckBox(this);EditText helperReason=input("Causa de ausencia del ayudante",m.helperAbsenceReason);if(row.helper!=null){helperPresented.setText("El ayudante se presentó");helperPresented.setChecked(!m.evaluated||m.helperPresented);form.addView(helperPresented);form.addView(helperReason);}
        String assignedMaterial=presentationSource(row.presentation);form.addView(label("Material asignado: "+(assignedMaterial.isEmpty()?"sin material indicado":assignedMaterial),13,false));CheckBox material=new CheckBox(this);material.setText("Se apegó al material asignado");material.setChecked(m.materialAdhered);CheckBox lesson=new CheckBox(this);lesson.setText("Cumplió la lección: "+(row.presentation.lesson.isEmpty()?"sin lección indicada":row.presentation.lesson));lesson.setChecked(m.lessonFulfilled);EditText lessonComments=input("Comentarios sobre la lección",m.lessonComments);lessonComments.setSingleLine(false);lessonComments.setMinLines(2);EditText comments=input("Comentarios particulares de la presentación",m.evaluationComments);comments.setSingleLine(false);comments.setMinLines(3);form.addView(material);form.addView(lesson);form.addView(lessonComments);form.addView(comments);
        Runnable visibility=()->{int show=studentPresented.isChecked()?View.GONE:View.VISIBLE;studentReason.setVisibility(show);replacementTitle.setVisibility(show);replacement.setVisibility(show);if(row.helper!=null)helperReason.setVisibility(helperPresented.isChecked()?View.GONE:View.VISIBLE);};studentPresented.setOnCheckedChangeListener((b,c)->visibility.run());helperPresented.setOnCheckedChangeListener((b,c)->visibility.run());visibility.run();
        final Handler handler=new Handler(Looper.getMainLooper());final long[] elapsed={Math.max(0,m.actualSeconds)*1000L},started={0};final boolean[] running={false};Runnable ticker=new Runnable(){@Override public void run(){long value=elapsed[0]+(running[0]?SystemClock.elapsedRealtime()-started[0]:0);clock.setText(formatSeconds((int)(value/1000)));if(running[0])handler.postDelayed(this,200);}};start.setOnClickListener(v->{if(!running[0]){running[0]=true;started[0]=SystemClock.elapsedRealtime();handler.post(ticker);}});pause.setOnClickListener(v->{if(running[0]){elapsed[0]+=SystemClock.elapsedRealtime()-started[0];running[0]=false;handler.removeCallbacks(ticker);clock.setText(formatSeconds((int)(elapsed[0]/1000)));}});reset.setOnClickListener(v->{running[0]=false;handler.removeCallbacks(ticker);elapsed[0]=0;clock.setText("00:00");});
        ScrollView sc=new ScrollView(this);sc.addView(form);AlertDialog d=safeDialog().setTitle("Evaluación").setView(sc).setNegativeButton("Cancelar",null).setPositiveButton("Finalizar y guardar",null).create();d.setOnDismissListener(x->handler.removeCallbacks(ticker));d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{if(running[0]){elapsed[0]+=SystemClock.elapsedRealtime()-started[0];running[0]=false;handler.removeCallbacks(ticker);}if(!studentPresented.isChecked()&&value(studentReason).isEmpty()){toast("Indique la causa por la que el estudiante no presentó.");return;}if(row.helper!=null&&!helperPresented.isChecked()&&value(helperReason).isEmpty()){toast("Indique la causa de ausencia del ayudante.");return;}m.actualSeconds=(int)(elapsed[0]/1000);m.studentPresented=studentPresented.isChecked();m.studentAbsenceReason=value(studentReason);m.replacementStudentId=m.studentPresented?0:((Models.Student)replacement.getSelectedItem()).id;m.helperPresented=row.helper==null||helperPresented.isChecked();m.helperAbsenceReason=row.helper==null?"":value(helperReason);m.materialAdhered=material.isChecked();m.lessonFulfilled=lesson.isChecked();m.lessonComments=value(lessonComments);m.evaluationComments=value(comments);m.evaluated=true;db.saveEvaluation(m);d.dismiss();showEvaluation(row.presentation.date,m.id);}));d.show();
    }

    private String formatSeconds(int total){int safe=Math.max(0,total);return String.format(Locale.ROOT,"%02d:%02d",safe/60,safe%60);}

    private void selectWeekToDelete(){LinearLayout form=column();EditText date=dateInput(activeWeek);form.addView(label("Seleccione la fecha de la semana que desea eliminar",14,true));form.addView(date);AlertDialog d=safeDialog().setTitle("Eliminar semana").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Continuar",null).create();d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{String day=value(date);try{LocalDate.parse(day);}catch(Exception e){toast("Seleccione una fecha válida.");return;}MinisterioDb.WeekDeleteInfo info=db.weekDeleteInfo(day);if(info.isEmpty()){toast("No hay una semana programada para "+day+".");return;}d.dismiss();confirmWeekDeletion(day,info);}));d.show();}

    private void confirmWeekDeletion(String day,MinisterioDb.WeekDeleteInfo info){String message="Fecha: "+day+"\nEscuela · presentaciones: "+info.presentations+"\nEscuela · asignaciones: "+info.movements+"\nVida y Ministerio · partes: "+info.lifeParts+"\nVida y Ministerio · participantes: "+info.lifeAssignments+"\nAsignaciones Mecánicas · registros: "+info.mechanicalPrograms+"\n\nSe eliminarán definitivamente la configuración compartida, los programas, las asignaciones y sus evaluaciones. La semana dejará de aparecer en los PDF.";safeDialog().setTitle("¿Eliminar esta semana definitivamente?").setMessage(message).setNegativeButton("Cancelar",null).setPositiveButton("Eliminar definitivamente",(dialog,which)->{for(long id:info.movementIds)ReminderScheduler.cancel(this,id);for(long id:info.lifeAssignmentIds)ReminderScheduler.cancelLife(this,id);db.deleteWeek(day);showActiveWeekOnly=false;SharedPreferences.Editor state=appState.edit().putBoolean("show_active_week_only",false);if(day.equals(activeWeek)){activeWeek="";state.remove("active_week");}state.apply();selectedMonth=day.substring(0,7);toast("Semana "+day+" eliminada definitivamente.");if("LIFE".equals(moduleMode))showLifeMinistryProgram();else if("MECHANICAL".equals(moduleMode))showMechanicalProgram();else showProgram();}).show();}

    private void editMeeting(){
        LinearLayout form=column();EditText date=dateInput(activeWeek);List<Models.President> presidents=new ArrayList<>();Models.President none=new Models.President();none.name="Sin seleccionar";presidents.add(none);presidents.addAll(db.presidents());Spinner president=spinner(presidents);CheckBox suspended=new CheckBox(this);suspended.setText("Reunión suspendida / asamblea");CheckBox visit=new CheckBox(this);visit.setText("Visita: usar solamente Sala Principal");EditText overseer=input("Nombre del superintendente de circuito", ""),talk=input("Tema del discurso de servicio", "");Spinner song=songSpinner("");form.addView(date);form.addView(label("Presidente (catálogo independiente)",14,true));form.addView(president);form.addView(suspended);form.addView(visit);form.addView(label("Discurso de servicio durante la visita",14,true));form.addView(overseer);form.addView(talk);form.addView(label("Canción del discurso de servicio",14,true));form.addView(song);visit.setOnCheckedChangeListener((button,checked)->{int visibility=checked?View.VISIBLE:View.GONE;overseer.setVisibility(visibility);talk.setVisibility(visibility);song.setVisibility(visibility);});visit.setChecked(false);overseer.setVisibility(View.GONE);talk.setVisibility(View.GONE);song.setVisibility(View.GONE);if(!activeWeek.isEmpty())loadMeetingForm(activeWeek,presidents,president,suspended,visit,overseer,talk,song);
        date.setOnClickListener(v->openDatePicker(date,()->loadMeetingForm(value(date),presidents,president,suspended,visit,overseer,talk,song)));
        AlertDialog d=safeDialog().setTitle("Configurar semana").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{String selectedDay=value(date);LocalDate parsed;try{parsed=LocalDate.parse(selectedDay);}catch(Exception e){toast("Use una fecha válida AAAA-MM-DD.");return;}String day=visit.isChecked()?parsed.with(TemporalAdjusters.previousOrSame(DayOfWeek.TUESDAY)).toString():selectedDay;List<Models.Meeting> annual=db.meetings(day.substring(0,4));long suspendedCount=annual.stream().filter(m->m.suspended&&!m.date.equals(day)&&!m.date.equals(selectedDay)).count();long visitCount=annual.stream().filter(m->m.visit&&!m.date.equals(day)&&!m.date.equals(selectedDay)).count();if(suspended.isChecked()&&suspendedCount>=3){toast("Ya se marcaron las 3 reuniones suspendidas de ese año.");return;}if(visit.isChecked()&&visitCount>=2){toast("Ya se marcaron las 2 visitas de ese año.");return;}if(visit.isChecked()&&!suspended.isChecked()&&(value(overseer).isEmpty()||value(talk).isEmpty()||selectedSong(song).isEmpty())){toast("Indique el superintendente de circuito, el tema y la canción.");return;}try{if(!day.equals(selectedDay))db.moveWeekDate(selectedDay,day);Models.Meeting m=new Models.Meeting();m.date=day;m.suspended=suspended.isChecked();m.visit=visit.isChecked();m.presidentId=((Models.President)president.getSelectedItem()).id;m.circuitOverseer=value(overseer);m.serviceTalkTopic=value(talk);m.serviceSong=selectedSong(song);db.saveMeeting(m);if(m.visit)for(long id:db.visitBookAssignmentIds(day))ReminderScheduler.cancelLife(this,id);activeWeek=day;showActiveWeekOnly=true;appState.edit().putString("active_week",activeWeek).putBoolean("show_active_week_only",true).apply();selectedMonth=day.substring(0,7);d.dismiss();if(!day.equals(selectedDay))toast("Semana de visita trasladada al martes "+day+".");showCurrentProgram();}catch(Exception error){toast("No se pudo configurar la semana: "+error.getMessage());}}));d.show();
    }

    private void loadMeetingForm(String date,List<Models.President> presidents,Spinner president,CheckBox suspended,CheckBox visit,EditText overseer,EditText talk,Spinner song){try{LocalDate.parse(date);}catch(Exception e){return;}Models.Meeting m=db.meeting(date);selectById(president,presidents,m.presidentId);suspended.setChecked(m.suspended);visit.setChecked(m.visit);overseer.setText(m.circuitOverseer);talk.setText(m.serviceTalkTopic);selectSong(song,m.serviceSong);}

    private static final class HelperChoice { Models.Student student;String text;@Override public String toString(){return text;} }

    private void editMovement(Models.Presentation presentation,String room,Models.ProgramRow existing){
        boolean individual=presentation.isIndividualMaleAssignment();long ignoredMovementId=existing==null?0:existing.movement.id;List<Models.Student> students=db.students(false);students.removeIf(s->db.personIsUsedInOtherRoom(s.id,presentation.id,room,ignoredMovementId));if(individual)students.removeIf(Models.Student::isFemale);if(students.isEmpty()){toast("No hay alumnos disponibles para esta asignación en la otra sala.");return;}LinearLayout form=column();form.setPadding(dp(10),0,dp(10),0);Spinner main=spinner(students);Spinner helper=new Spinner(this);TextView helperTitle=label("Ayudantes sugeridos",14,true);CheckBox emergency=new CheckBox(this);emergency.setText("Asignación emergente");EditText actual=input("Tiempo realizado (opcional)",existing==null?"":String.valueOf(existing.movement.actualMinutes));actual.setInputType(InputType.TYPE_CLASS_NUMBER);form.addView(label(presentation.toString()+"\nSala "+(room.equals("PRINCIPAL")?"Principal":"Auxiliar"),15,true));form.addView(label("Estudiante",14,true));form.addView(main);if(!individual){form.addView(helperTitle);form.addView(helper);}form.addView(emergency);form.addView(actual);
        if(existing!=null){selectById(main,students,existing.movement.studentId);emergency.setChecked(existing.movement.emergency);}
        Runnable refresh=()->{Models.Student selected=(Models.Student)main.getSelectedItem();List<HelperChoice> choices=new ArrayList<>();if(individual){HelperChoice h=new HelperChoice();h.text="No aplica para esta asignación individual";choices.add(h);helper.setEnabled(false);}else{helper.setEnabled(true);for(MinisterioDb.HelperSuggestion suggestion:db.helperSuggestions(selected.id,presentation.date,presentation.id,room,ignoredMovementId)){HelperChoice h=new HelperChoice();h.student=suggestion.student;String last=suggestion.days==Long.MAX_VALUE?"sin asignaciones anteriores":"última asignación hace "+suggestion.days+" días";String together=suggestion.lastTogetherDate==null?"nunca asignados juntos":"última vez juntos: "+suggestion.lastTogetherDate;h.text=suggestion.student.name+" · "+last+" · "+together;choices.add(h);}}helper.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,choices));if(existing!=null)for(int i=0;i<choices.size();i++)if(choices.get(i).student!=null&&choices.get(i).student.id==existing.movement.helperId)helper.setSelection(i);};
        main.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){@Override public void onItemSelected(AdapterView<?>p,View v,int pos,long id){refresh.run();}@Override public void onNothingSelected(AdapterView<?>p){}});refresh.run();
        AlertDialog d=safeDialog().setTitle(existing==null?"Asignar alumno":"Modificar asignación").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{Models.Movement m=existing==null?new Models.Movement():existing.movement;m.presentationId=presentation.id;m.studentId=((Models.Student)main.getSelectedItem()).id;m.room=room;m.role=presentation.isBibleReading()?"LECTOR":"MAESTRO";HelperChoice hc=individual?null:(HelperChoice)helper.getSelectedItem();m.helperId=hc==null||hc.student==null?0:hc.student.id;m.emergency=emergency.isChecked();m.actualMinutes=intValue(actual,0);m.lesson=presentation.lesson;String error=db.validateMovement(m);if(error!=null){toast(error);return;}db.saveMovement(m);Models.ProgramRow row=findRow(db.program(selectedMonth),presentation.id,room);if(row!=null)ReminderScheduler.schedule(this,row);d.dismiss();showProgram();}));d.show();
    }

    private void addExcelTools(LinearLayout body) {
        body.addView(label("Carga los tres catálogos desde un solo libro con las hojas Alumnos, Presidentes y Presentaciones.",13,false));
        HorizontalScrollView scroll = new HorizontalScrollView(this); LinearLayout actions = row();
        actions.addView(button("Importar Excel",v->openExcelImporter()));
        actions.addView(button("Compartir plantilla",v->shareExcelTemplate()));
        scroll.addView(actions); body.addView(scroll);
    }

    private void reviewDuplicates(){
        MinisterioDb.DuplicateReview r=db.reviewDuplicates();StringBuilder msg=new StringBuilder("Duplicados exactos encontrados:\n\nAlumnos: ").append(r.studentDuplicates).append("\nPresidentes: ").append(r.presidentDuplicates);
        if(!r.ambiguous.isEmpty()){msg.append("\n\nCoincidencias ambiguas (no se modificarán):");for(String name:r.ambiguous)msg.append("\n• ").append(name);}
        if(r.studentDuplicates+r.presidentDuplicates==0){safeDialog().setTitle("Revisión terminada").setMessage(msg.append("\n\nNo hay duplicados exactos que depurar.")).setPositiveButton("Aceptar",null).show();return;}
        safeDialog().setTitle("Revisar duplicados").setMessage(msg.append("\n\nLa aplicación creará un respaldo, conservará un solo registro de cada duplicado exacto y trasladará sus asignaciones al registro conservado.")).setNegativeButton("Cancelar",null).setPositiveButton("Respaldar y depurar",(d,w)->cleanupDuplicates()).show();
    }

    private void cleanupDuplicates(){try{MinisterioDb.DuplicateCleanupResult r=db.cleanupExactDuplicates();StringBuilder msg=new StringBuilder("Depuración terminada.\n\nAlumnos unificados: ").append(r.studentDuplicates).append("\nPresidentes unificados: ").append(r.presidentDuplicates).append("\nRespaldo: ").append(r.backupName);if(!r.ambiguous.isEmpty())msg.append("\n\nLas coincidencias ambiguas se conservaron para revisión manual.");safeDialog().setTitle("Duplicados corregidos").setMessage(msg).setPositiveButton("Aceptar",(d,w)->showCurrentProgram()).show();}catch(Exception e){safeDialog().setTitle("No se pudo depurar").setMessage(e.getMessage()).setPositiveButton("Aceptar",null).show();}}

    private void openExcelImporter() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(EXCEL_MIME);
        intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{EXCEL_MIME,"application/vnd.ms-excel"});
        try { startActivityForResult(intent,REQUEST_IMPORT_EXCEL); }
        catch (ActivityNotFoundException e) { toast("No se encontró un selector de archivos en el dispositivo."); }
    }

    private void openHistoricalImporter(){EditText year=input("Año del histórico",String.valueOf(pendingHistoricalYear));year.setInputType(InputType.TYPE_CLASS_NUMBER);LinearLayout form=column();form.setPadding(dp(16),0,dp(16),0);form.addView(label("Rutina extraordinaria y separada de la importación cotidiana. Reconoce verde como estudiante, azul como ayudante y amarillo como estudiante durante visita.",14,false));form.addView(year);AlertDialog d=safeDialog().setTitle("Importar histórico de asignaciones").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Seleccionar archivo",null).create();d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{int selected=intValue(year,0);if(selected<2000||selected>2100){toast("Indique un año válido.");return;}pendingHistoricalYear=selected;d.dismiss();Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(EXCEL_MIME);try{startActivityForResult(intent,REQUEST_IMPORT_HISTORY);}catch(ActivityNotFoundException e){toast("No se encontró un selector de archivos.");}}));d.show();}

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQUEST_SUPPORT_SAVE){
            File file=pendingSupportBackup;
            if(file==null){String saved=appState.getString("pending_support_backup","");if(!saved.isEmpty())file=new File(saved);}
            pendingSupportBackup=null;appState.edit().remove("pending_support_backup").apply();
            if(file!=null&&!file.getParentFile().equals(new File(getCacheDir(),"reportes")))file=null;
            if(resultCode==RESULT_OK&&data!=null&&data.getData()!=null&&file!=null){
                try(InputStream source=new java.io.FileInputStream(file);java.io.OutputStream destination=getContentResolver().openOutputStream(data.getData())){
                    if(destination==null)throw new IllegalStateException("No se pudo guardar en el destino elegido.");
                    byte[] buffer=new byte[8192];int read;while((read=source.read(buffer))!=-1)destination.write(buffer,0,read);
                    toast("Respaldo cifrado guardado en la ubicación seleccionada.");
                }catch(Exception error){toast("No se pudo guardar el respaldo: "+error.getMessage());}
            }return;
        }
        if(requestCode==REQUEST_SUPPORT_RESTORE){if(supportUser!=null&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null)confirmSupportRestore(data.getData());return;}
        if(requestCode==REQUEST_IMPORT_HISTORY){if(resultCode==RESULT_OK&&data!=null&&data.getData()!=null)previewHistoricalImport(data.getData());return;}
        if(requestCode==REQUEST_IMPORT_LIFE_MINISTRY){if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;try(InputStream input=getContentResolver().openInputStream(data.getData())){if(input==null)throw new IllegalArgumentException("No fue posible abrir el archivo seleccionado.");LifeMinistryExcelImportManager.Result result=LifeMinistryExcelImportManager.importWorkbook(input,db);if(result.lastMonth!=null)selectedMonth=result.lastMonth;safeDialog().setTitle(result.errors.isEmpty()&&result.warnings.isEmpty()?"Programa importado":"Programa importado con observaciones").setMessage(result.summary()).setPositiveButton("Ver programa",(dialog,which)->showLifeMinistryProgram()).show();}catch(Exception error){safeDialog().setTitle("No se pudo importar Vida y Ministerio").setMessage(error.getMessage()==null?"Revise que sea un archivo XLSX basado en la plantilla separada.":error.getMessage()).setPositiveButton("Aceptar",null).show();}return;}
        if(requestCode!=REQUEST_IMPORT_EXCEL||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        try(InputStream input=getContentResolver().openInputStream(data.getData())){
            if(input==null)throw new IllegalArgumentException("No fue posible abrir el archivo seleccionado.");
            ExcelImportManager.Result result=ExcelImportManager.importWorkbook(input,db);
            if(result.lastPresentationMonth!=null)selectedMonth=result.lastPresentationMonth;
            safeDialog().setTitle(result.errors.isEmpty()&&result.warnings.isEmpty()?"Excel importado":"Excel importado con observaciones")
                    .setMessage(result.summary()).setPositiveButton("Ver programa",(dialog,which)->showCurrentProgram()).show();
        }catch(Exception e){safeDialog().setTitle("No se pudo importar el Excel").setMessage(e.getMessage()==null?"Revise que sea un archivo XLSX basado en la plantilla.":e.getMessage()).setPositiveButton("Aceptar",null).show();}
    }

    private void previewHistoricalImport(Uri uri){try(InputStream input=getContentResolver().openInputStream(uri)){if(input==null)throw new IllegalArgumentException("No fue posible abrir el archivo.");HistoricalImportManager.Preview preview=HistoricalImportManager.prepare(input,db,pendingHistoricalYear);AlertDialog dialog=safeDialog().setTitle("Revisar importación histórica").setMessage(preview.summary()+"\n\nSolo se guardarán los movimientos listos. Se creará un respaldo antes de escribir.").setNegativeButton("Cancelar",null).setPositiveButton("Crear respaldo e importar",null).create();dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{try{HistoricalImportManager.ImportResult result=preview.importInto(db);dialog.dismiss();safeDialog().setTitle("Histórico importado").setMessage(result.summary()).setPositiveButton("Ver historial",(a,b)->showHistory()).show();}catch(Exception e){safeDialog().setTitle("No se pudo importar").setMessage(e.getMessage()).setPositiveButton("Aceptar",null).show();}}));dialog.show();}catch(Exception e){safeDialog().setTitle("No se pudo leer el histórico").setMessage(e.getMessage()==null?"Revise que sea el archivo HISTORICO esperado.":e.getMessage()).setPositiveButton("Aceptar",null).show();}}

    private void shareExcelTemplate() {
        File directory=new File(getCacheDir(),"reportes");if(!directory.exists()&&!directory.mkdirs()){toast("No fue posible preparar la plantilla.");return;}
        File target=new File(directory,"Plantilla_Escuela_Ministerio.xlsx");
        try(InputStream input=getAssets().open("Plantilla_Escuela_Ministerio.xlsx");FileOutputStream output=new FileOutputStream(target)){
            byte[] buffer=new byte[8192];int read;while((read=input.read(buffer))!=-1)output.write(buffer,0,read);
            shareFile(target,EXCEL_MIME,"Plantilla para importar alumnos, presidentes y presentaciones",null);
        }catch(Exception e){toast("No fue posible compartir la plantilla: "+e.getMessage());}
    }

    private void createMonthlyPdf(){try{File f=new ReportManager(this,db).monthlyPdf(selectedMonth);shareFile(f,"application/pdf","Programa mensual "+selectedMonth,null);}catch(Exception e){toast("No fue posible generar el PDF: "+e.getMessage());}}
    private void selectWeeklyRoomPdf(){LinearLayout form=column();EditText date=dateInput(activeWeek);Spinner room=spinner(Arrays.asList("Sala Principal","Sala Auxiliar"));form.addView(label("Semana del reporte",14,true));form.addView(date);form.addView(label("Sala que se incluirá",14,true));form.addView(room);AlertDialog d=safeDialog().setTitle("PDF semanal por sala").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Generar",null).create();d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{String day=value(date);try{LocalDate.parse(day);}catch(Exception e){toast("Seleccione una fecha válida.");return;}String selectedRoom=room.getSelectedItemPosition()==0?"PRINCIPAL":"AUXILIAR";try{File f=new ReportManager(this,db).weeklyRoomPdf(day,selectedRoom);d.dismiss();shareFile(f,"application/pdf","Programa semanal "+day+" · "+String.valueOf(room.getSelectedItem()),null);}catch(Exception e){toast("No fue posible generar el PDF: "+e.getMessage());}}));d.show();}
    private void createAssignmentsPdf(){try{File f=new ReportManager(this,db).assignmentPdf(selectedMonth);shareFile(f,"application/pdf","Hojas de asignación "+selectedMonth,null);}catch(Exception e){toast("No fue posible generar el PDF: "+e.getMessage());}}
    private void createAndShareJpg(Models.ProgramRow row){try{File f=new ReportManager(this,db).assignmentJpg(row);String president=row.president==null?"el presidente de la reunión":row.president.name;String message="Recordatorio: tienes la asignación «"+row.presentation.assignmentName+"» el "+row.presentation.date+" en Sala "+row.movement.room.toLowerCase(es)+". Si no podrás presentarla o necesitas un cambio, informa a "+president+".";shareFile(f,"image/jpeg",message,"com.whatsapp");}catch(Exception e){toast("No fue posible generar la imagen: "+e.getMessage());}}
    private void shareFile(File file,String mime,String text,String preferredPackage){Uri uri=new Uri.Builder().scheme("content").authority(getPackageName()+".files").appendPath(file.getName()).build();Intent send=new Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM,uri).putExtra(Intent.EXTRA_TEXT,text).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);if(preferredPackage!=null)send.setPackage(preferredPackage);try{startActivity(send);}catch(ActivityNotFoundException e){send.setPackage(null);startActivity(Intent.createChooser(send,"Compartir"));}}
    private void scheduleAllReminders(){List<Models.ProgramRow> rows=db.program(selectedMonth);for(Models.ProgramRow row:rows)ReminderScheduler.schedule(this,row);toast(rows.size()+" recordatorios revisados. Se avisará siete días antes a las 9:00.");if(android.os.Build.VERSION.SDK_INT>=31&&!getSystemService(android.app.AlarmManager.class).canScheduleExactAlarms())safeDialog().setMessage("Para avisos exactos, permita alarmas y recordatorios en la configuración del sistema.").setPositiveButton("Abrir configuración",(d,w)->startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())))).setNegativeButton("Después",null).show();}

    private void showHistory(){
        ScrollView page=page("Actividad por alumno");LinearLayout body=body(page);List<Models.Student> students=db.students(true);if(students.isEmpty()){body.addView(label("Primero registre alumnos.",16,false));return;}Spinner student=spinner(students);EditText consultation=dateInput(LocalDate.now().toString());body.addView(label("Alumno",14,true));body.addView(student);body.addView(label("Fecha de la consulta",14,true));body.addView(consultation);LinearLayout results=column();body.addView(button("Consultar",v->renderHistory((Models.Student)student.getSelectedItem(),value(consultation),results)));body.addView(results);renderHistory(students.get(0),value(consultation),results);
    }

    private void renderHistory(Models.Student student,String consultationDate,LinearLayout target){
        LocalDate consultation;try{consultation=LocalDate.parse(consultationDate);}catch(Exception e){toast("Seleccione una fecha de consulta válida.");return;}target.removeAllViews();List<Models.ProgramRow> history=db.history(student.id);String mainLast=null,auxLast=null;List<Models.ProgramRow> future=new ArrayList<>();
        for(Models.ProgramRow r:history){LocalDate date;try{date=LocalDate.parse(r.presentation.date);}catch(Exception ignored){continue;}if(date.isAfter(consultation)){future.add(r);continue;}if("PRINCIPAL".equals(r.movement.room)&&mainLast==null)mainLast=r.presentation.date;if("AUXILIAR".equals(r.movement.room)&&auxLast==null)auxLast=r.presentation.date;}
        target.addView(label("Última asignación hasta "+consultationDate+"\nSala Principal: "+elapsed(mainLast,consultation)+"\nSala Auxiliar: "+elapsed(auxLast,consultation),15,true));future.sort((a,b)->a.presentation.date.compareTo(b.presentation.date));StringBuilder upcoming=new StringBuilder("Asignaciones futuras:");if(future.isEmpty())upcoming.append(" sin asignaciones programadas");else for(Models.ProgramRow r:future){long days=ChronoUnit.DAYS.between(consultation,LocalDate.parse(r.presentation.date));boolean helper=r.movement.helperId==student.id;upcoming.append("\n").append(r.presentation.date).append(" · Sala ").append("PRINCIPAL".equals(r.movement.room)?"Principal":"Auxiliar").append(" · ").append(r.presentation.assignmentName).append(" · ").append(helper?"Ayudante":"Estudiante").append(" (faltan ").append(days).append(" días)");}target.addView(label(upcoming.toString(),14,true));
        if(history.isEmpty())target.addView(label("No hay actividad registrada.",15,false));
        for(Models.ProgramRow r:history){boolean helper=r.movement.helperId==student.id;boolean historical=r.movement.id<0;String partner=helper?(r.student==null?"—":r.student.name):(r.helper==null?"Sin ayudante":r.helper.name);LinearLayout c=card();c.addView(label(r.presentation.date+" · Sala "+(r.movement.room.equals("PRINCIPAL")?"Principal":"Auxiliar")+(r.movement.visit?" · Visita":""),15,true));c.addView(label(r.presentation.assignmentName+" · "+(helper?"Ayudante":"Estudiante"+(historical?"":" / "+r.movement.role))+"\nCompañero: "+partner+(historical?" · Registro histórico":" · Lección: "+r.presentation.lesson)+(r.movement.emergency?" · Emergente":"")+"\nPulse para ver la evaluación",13,false));c.setClickable(true);c.setFocusable(true);c.setOnClickListener(v->showHistoryEvaluation(student,r));target.addView(c);}
    }

    private void showHistoryEvaluation(Models.Student consulted,Models.ProgramRow row){boolean helper=row.movement.helperId==consulted.id;String role=helper?"Ayudante":("LECTOR".equals(row.movement.role)?"Lector":"Maestro");StringBuilder message=new StringBuilder("Fecha: ").append(row.presentation.date).append("\nSala: ").append("PRINCIPAL".equals(row.movement.room)?"Principal":"Auxiliar").append("\nParticipación consultada: ").append(role).append("\nAsignación: ").append(row.presentation.assignmentName);if(row.movement.id<0)message.append("\n\nEste registro proviene del histórico importado y no contiene una evaluación.");else if(!row.movement.evaluated)message.append("\n\nTodavía no se ha registrado una evaluación para esta asignación.");else{Models.Student replacement=db.student(row.movement.replacementStudentId);message.append("\n\nTiempo asignado: ").append(row.presentation.minutes).append(" min.").append("\nTiempo realizado: ").append(formatSeconds(row.movement.actualSeconds)).append("\n\nEstudiante: ").append(row.student==null?"—":row.student.name).append("\nSe presentó: ").append(row.movement.studentPresented?"Sí":"No");if(!row.movement.studentPresented)message.append("\nCausa: ").append(emptyText(row.movement.studentAbsenceReason)).append("\nSustituto: ").append(replacement==null?"Sin sustituto":replacement.name);if(row.helper!=null){message.append("\n\nAyudante: ").append(row.helper.name).append("\nSe presentó: ").append(row.movement.helperPresented?"Sí":"No");if(!row.movement.helperPresented)message.append("\nCausa: ").append(emptyText(row.movement.helperAbsenceReason));}message.append("\n\nApego al material: ").append(row.movement.materialAdhered?"Sí":"No").append("\nLección asignada: ").append(emptyText(row.presentation.lesson)).append("\nCumplió la lección: ").append(row.movement.lessonFulfilled?"Sí":"No").append("\nComentarios de la lección: ").append(emptyText(row.movement.lessonComments)).append("\nComentarios de la presentación: ").append(emptyText(row.movement.evaluationComments));}safeDialog().setTitle("Evaluación de la asignación").setMessage(message.toString()).setPositiveButton("Cerrar",null).show();}

    private String elapsed(String date,LocalDate consultation){if(date==null)return "sin registros";try{long days=ChronoUnit.DAYS.between(LocalDate.parse(date),consultation);return date+" (hace "+Math.max(0,days)+" días)";}catch(Exception e){return date;}}
    private String emptyText(String value){return value==null||value.trim().isEmpty()?"—":value;}

    private String presentationSource(Models.Presentation presentation){return presentation.source.isEmpty()?presentation.material:presentation.source;}
    private void showCurrentProgram(){if("LIFE".equals(moduleMode))showLifeMinistryProgram();else if("MECHANICAL".equals(moduleMode))showMechanicalProgram();else if("PUBLIC".equals(moduleMode))showPublicProgram();else showProgram();}

    private List<Models.Student> publicCandidates(String role){
        List<Models.Student> candidates=new ArrayList<>();
        Models.Student placeholder=new Models.Student();placeholder.name="Pendiente / sin asignar";candidates.add(placeholder);
        for(Models.Student student:db.students(false)){
            if(!student.isMale())continue;
            if("PRESIDENTE".equals(role)||"ORADOR".equals(role)&&student.discursoPublico
                    ||"CONDUCTOR".equals(role)&&student.atalaya||"LECTOR".equals(role)&&student.lectorAtalaya)
                candidates.add(student);
        }
        return candidates;
    }

    private String publicName(long id){Models.Student student=db.student(id);return student==null?"Pendiente":student.name;}
    private String speakerName(Models.PublicMeeting meeting){
        if("VISITOR".equals(meeting.speakerKind)){
            Models.VisitingSpeaker visitor=db.visitingSpeaker(meeting.visitorSpeakerId);
            return visitor==null?"Pendiente":visitor.name+" · "+visitor.congregation;
        }
        return publicName(meeting.localSpeakerId);
    }

    private void showPublicProgram(){
        ScrollView page=page("Reunión Pública y Atalaya");LinearLayout body=body(page);
        body.addView(label("Mes del programa",14,true));
        Button month=button(selectedMonthLabel(),v->openMonthPicker(this::showPublicProgram));
        month.setAllCaps(false);month.setCompoundDrawablesWithIntrinsicBounds(0,0,android.R.drawable.ic_menu_my_calendar,0);body.addView(month);
        body.addView(label("La reunión se programa normalmente el domingo, pero puede elegirse otra fecha. Los participantes se toman del catálogo común de alumnos.",13,false));
        body.addView(button("+ Configurar nueva reunión",v->editPublicMeeting(null)));
        List<Models.PublicMeeting> weeks=db.publicMeetings(selectedMonth);
        if(weeks.isEmpty())body.addView(label("No hay reuniones del fin de semana registradas para este mes.",15,false));
        for(Models.PublicMeeting week:weeks){
            LinearLayout card=card();
            card.addView(label(week.date+(week.suspended?" · CANCELADA POR ASAMBLEA":week.visit?" · VISITA":""),
                    18,true));
            if(week.suspended)card.addView(label("Sin programa ni asignaciones mecánicas activas.",14,false));
            else{
                card.addView(label("Presidente: "+publicName(week.presidentId),14,false));
                card.addView(label("DISCURSO PÚBLICO · "+speakerName(week),15,true));
                card.addView(label("Bosquejo "+emptyText(week.outlineNumber)+": "+emptyText(week.outlineTitle),13,false));
                card.addView(label("ESTUDIO DE LA ATALAYA · Conductor: "+publicName(week.conductorId),15,true));
                card.addView(label("Tema: "+emptyText(week.watchtowerTopic),13,false));
                if(week.visit)card.addView(label("DISCURSO FINAL · "+speakerName(week)+"\nTítulo: "+emptyText(week.finalTalkTitle)+"\nNo se programa lector durante la visita.",14,true));
                else card.addView(label("Lector: "+publicName(week.readerId),13,false));
                Models.MechanicalProgram mechanic=db.mechanicalProgram(week.date,"WEEKEND");
                card.addView(label("Asignaciones mecánicas: "+(mechanic==null?"pendientes":"registradas"),13,false));
            }
            LinearLayout actions=row();
            actions.addView(button("Editar",v->editPublicMeeting(week)));
            if(!week.suspended){
                actions.addView(button("Evaluar orador",v->editPublicEvaluation(week)));
                actions.addView(button("Mecánicas",v->{selectedMonth=week.date.substring(0,7);activeWeek=week.date;showActiveWeekOnly=true;appState.edit().putString("active_week",activeWeek).putBoolean("show_active_week_only",true).apply();buildMechanicalShell();showMechanicalProgram();}));
            }
            actions.addView(button("Eliminar semana",v->confirm(
                    "¿Eliminar definitivamente la reunión del "+week.date+" y sus asignaciones mecánicas de fin de semana? El historial y la evaluación de esta reunión también se eliminarán.",
                    ()->{db.deletePublicMeeting(week.date);showPublicProgram();})));
            HorizontalScrollView horizontal=new HorizontalScrollView(this);horizontal.addView(actions);card.addView(horizontal);
            body.addView(card);
        }
    }

    private void editPublicMeeting(Models.PublicMeeting existing){editPublicMeeting(existing,this::showPublicProgram);}
    private void editPublicMeeting(Models.PublicMeeting existing,Runnable afterSave){
        Models.PublicMeeting week=existing==null?new Models.PublicMeeting():existing;
        LinearLayout form=column();form.setPadding(dp(12),0,dp(12),dp(12));
        LocalDate suggested=YearMonth.parse(selectedMonth).atDay(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        EditText date=dateInput(existing==null?suggested.toString():week.date);
        if(existing!=null)date.setEnabled(false);
        form.addView(label("Fecha de la reunión",14,true));form.addView(date);
        List<Models.Student> presidents=publicCandidates("PRESIDENTE");
        Spinner president=spinner(presidents);selectById(president,presidents,week.presidentId);
        form.addView(label("Presidente · catálogo de alumnos",14,true));form.addView(president);
        CheckBox assembly=checkBox("Cancelada por asamblea",week.suspended),visit=checkBox("Semana de la visita",week.visit);
        form.addView(assembly);form.addView(visit);
        LinearLayout program=column();form.addView(program);
        program.addView(label("DISCURSO PÚBLICO",16,true));
        Spinner source=spinner(Arrays.asList("Orador local","Orador visitante"));
        source.setSelection("VISITOR".equals(week.speakerKind)?1:0);
        program.addView(label("Origen del orador",14,true));program.addView(source);
        List<Models.Student> locals=publicCandidates("ORADOR");Spinner local=spinner(locals);
        selectById(local,locals,week.localSpeakerId);
        TextView localLabel=label("Orador local autorizado",14,true);program.addView(localLabel);program.addView(local);
        List<Models.VisitingSpeaker> visitors=new ArrayList<>();
        Models.VisitingSpeaker empty=new Models.VisitingSpeaker();empty.name="Pendiente / sin asignar";visitors.add(empty);
        visitors.addAll(db.visitingSpeakers());Spinner visitor=spinner(visitors);
        selectById(visitor,visitors,week.visitorSpeakerId);
        TextView visitorLabel=label("Orador visitante / superintendente de circuito",14,true);program.addView(visitorLabel);program.addView(visitor);
        if(visitors.size()==1)program.addView(label("Para elegir un visitante, regístrelo primero en Oradores visitantes.",13,false));
        EditText outline=input("Número de bosquejo",week.outlineNumber),title=input("Título del bosquejo",week.outlineTitle);
        program.addView(outline);program.addView(title);
        program.addView(label("ESTUDIO DE LA ATALAYA",16,true));
        List<Models.Student> conductors=publicCandidates("CONDUCTOR");Spinner conductor=spinner(conductors);
        selectById(conductor,conductors,week.conductorId);
        program.addView(label("Conductor autorizado",14,true));program.addView(conductor);
        EditText theme=input("Tema de la Atalaya",week.watchtowerTopic);program.addView(theme);
        List<Models.Student> readers=publicCandidates("LECTOR");Spinner reader=spinner(readers);
        selectById(reader,readers,week.readerId);
        TextView readerLabel=label("Lector autorizado",14,true);program.addView(readerLabel);program.addView(reader);
        TextView finalLabel=label("DISCURSO FINAL · visita",16,true);program.addView(finalLabel);
        EditText finalTitle=input("Título del discurso final",week.finalTalkTitle);program.addView(finalTitle);
        Runnable visibility=()->{
            boolean suspended=assembly.isChecked(),duringVisit=visit.isChecked();
            program.setVisibility(suspended?View.GONE:View.VISIBLE);
            source.setSelection(duringVisit?1:source.getSelectedItemPosition());
            source.setEnabled(!duringVisit);
            boolean chooseVisitor=duringVisit||source.getSelectedItemPosition()==1;
            localLabel.setVisibility(chooseVisitor?View.GONE:View.VISIBLE);local.setVisibility(chooseVisitor?View.GONE:View.VISIBLE);
            visitorLabel.setVisibility(chooseVisitor?View.VISIBLE:View.GONE);visitor.setVisibility(chooseVisitor?View.VISIBLE:View.GONE);
            readerLabel.setVisibility(duringVisit?View.GONE:View.VISIBLE);reader.setVisibility(duringVisit?View.GONE:View.VISIBLE);
            finalLabel.setVisibility(duringVisit?View.VISIBLE:View.GONE);finalTitle.setVisibility(duringVisit?View.VISIBLE:View.GONE);
        };
        assembly.setOnCheckedChangeListener((v,checked)->visibility.run());
        visit.setOnCheckedChangeListener((v,checked)->visibility.run());visibility.run();
        source.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){visibility.run();}
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=safeDialog().setTitle(existing==null?"Configurar reunión":"Editar reunión")
                .setView(scroll).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();
        dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{
            try{
                String day=LocalDate.parse(value(date)).toString();
                if(existing==null&&db.publicMeeting(day)!=null){toast("La reunión de esta fecha ya existe. Use Editar.");return;}
                Models.PublicMeeting toSave=new Models.PublicMeeting();
                toSave.date=day;toSave.suspended=assembly.isChecked();toSave.visit=visit.isChecked();
                toSave.presidentId=((Models.Student)president.getSelectedItem()).id;
                toSave.speakerKind=toSave.visit||source.getSelectedItemPosition()==1?"VISITOR":"LOCAL";
                toSave.localSpeakerId=((Models.Student)local.getSelectedItem()).id;
                toSave.visitorSpeakerId=((Models.VisitingSpeaker)visitor.getSelectedItem()).id;
                toSave.outlineNumber=value(outline);toSave.outlineTitle=value(title);
                toSave.conductorId=((Models.Student)conductor.getSelectedItem()).id;
                toSave.watchtowerTopic=value(theme);
                toSave.readerId=toSave.visit?0:((Models.Student)reader.getSelectedItem()).id;
                toSave.finalTalkTitle=toSave.visit?value(finalTitle):"";
                toSave.evaluated=week.evaluated;toSave.speakerPresented=week.speakerPresented;
                toSave.evaluationComments=week.evaluationComments;
                if(existing!=null&&(existing.localSpeakerId!=toSave.localSpeakerId||existing.visitorSpeakerId!=toSave.visitorSpeakerId
                        ||!existing.speakerKind.equals(toSave.speakerKind))){
                    toSave.evaluated=false;toSave.speakerPresented=true;toSave.evaluationComments="";
                }
                db.savePublicMeeting(toSave);
                selectedMonth=day.substring(0,7);dialog.dismiss();afterSave.run();
            }catch(Exception error){toast("No se pudo guardar: "+error.getMessage());}
        }));dialog.show();
    }

    private void editPublicEvaluation(Models.PublicMeeting week){
        LinearLayout form=column();form.setPadding(dp(12),0,dp(12),dp(12));
        form.addView(label(week.date+" · "+speakerName(week),15,true));
        CheckBox presented=checkBox("Presentó el discurso público",week.speakerPresented);
        EditText notes=input("Comentarios de la evaluación",week.evaluationComments);
        notes.setSingleLine(false);notes.setMinLines(3);
        form.addView(presented);form.addView(notes);
        safeDialog().setTitle("Evaluación del orador").setView(form).setNegativeButton("Cancelar",null)
                .setPositiveButton("Guardar",(dialog,which)->{db.savePublicSpeakerEvaluation(week.date,presented.isChecked(),value(notes));showPublicProgram();}).show();
    }

    private void showVisitingSpeakers(){
        ScrollView page=page("Oradores visitantes");LinearLayout body=body(page);
        body.addView(button("+ Agregar orador visitante",v->editVisitingSpeaker(null)));
        List<Models.VisitingSpeaker> visitors=db.visitingSpeakers();
        if(visitors.isEmpty())body.addView(label("No hay oradores visitantes registrados.",15,false));
        for(Models.VisitingSpeaker visitor:visitors){
            LinearLayout card=card();card.addView(label(visitor.name,17,true));
            card.addView(label("Congregación: "+emptyText(visitor.congregation)+"\nLocalidad: "+emptyText(visitor.locality)+"\nNombramiento: "+emptyText(visitor.appointment),13,false));
            List<Models.PublicMeeting> history=db.publicSpeakerHistory("VISITOR",visitor.id);
            card.addView(label("Discurso público · participaciones: "+history.size(),13,true));
            for(Models.PublicMeeting meeting:history)card.addView(button(
                    meeting.date+" · "+emptyText(meeting.outlineTitle),v->showPublicHistoryDetail(meeting)));
            LinearLayout actions=row();actions.addView(button("Editar",v->editVisitingSpeaker(visitor)));
            actions.addView(button("Eliminar",v->confirm("¿Eliminar a "+visitor.name+" del catálogo?",
                    ()->{if(db.deleteVisitingSpeaker(visitor.id))showVisitingSpeakers();else toast("No se puede eliminar: tiene reuniones en su historial.");})));
            card.addView(actions);body.addView(card);
        }
    }

    private void editVisitingSpeaker(Models.VisitingSpeaker old){
        LinearLayout form=column();form.setPadding(dp(12),0,dp(12),dp(12));
        EditText name=input("Nombre del orador",old==null?"":old.name);
        EditText congregation=input("Congregación",old==null?"":old.congregation);
        EditText locality=input("Localidad",old==null?"":old.locality);
        EditText appointment=input("Nombramiento",old==null?"":old.appointment);
        for(EditText field:new EditText[]{name,congregation,locality,appointment})form.addView(field);
        AlertDialog dialog=safeDialog().setTitle(old==null?"Agregar orador visitante":"Editar orador visitante")
                .setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();
        dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{
            Models.VisitingSpeaker visitor=new Models.VisitingSpeaker();visitor.id=old==null?0:old.id;
            visitor.name=value(name);visitor.congregation=value(congregation);
            visitor.locality=value(locality);visitor.appointment=value(appointment);
            try{db.saveVisitingSpeaker(visitor);dialog.dismiss();showVisitingSpeakers();}
            catch(Exception error){toast(error.getMessage());}
        }));dialog.show();
    }

    private void showPublicHistory(){
        ScrollView page=page("Historial de Discurso Público y lectores");LinearLayout body=body(page);
        body.addView(label("Oradores visitantes",17,true));
        for(Models.VisitingSpeaker visitor:db.visitingSpeakers())
            addPublicHistory(body,visitor.name,db.publicSpeakerHistory("VISITOR",visitor.id),false);
        body.addView(label("Oradores locales",17,true));
        for(Models.Student student:db.students(true))
            addPublicHistory(body,student.name,db.publicSpeakerHistory("LOCAL",student.id),false);
        body.addView(label("Lectores de la Atalaya",17,true));
        for(Models.Student student:db.students(true))
            addPublicHistory(body,student.name,db.publicReaderHistory(student.id),true);
    }

    private void addPublicHistory(LinearLayout body,String name,List<Models.PublicMeeting> history,boolean reader){
        if(history.isEmpty())return;
        LinearLayout card=card();card.addView(label(name+" · "+history.size()+" participación(es)",15,true));
        for(Models.PublicMeeting meeting:history)card.addView(button(meeting.date+" · "+
                emptyText(reader?meeting.watchtowerTopic:meeting.outlineTitle),v->showPublicHistoryDetail(meeting)));
        body.addView(card);
    }

    private void showPublicHistoryDetail(Models.PublicMeeting meeting){
        String text="Fecha: "+meeting.date+"\nDiscurso público: "+speakerName(meeting)+"\nBosquejo "+
                emptyText(meeting.outlineNumber)+": "+emptyText(meeting.outlineTitle)+
                "\nConductor de la Atalaya: "+publicName(meeting.conductorId)+
                "\nTema de la Atalaya: "+emptyText(meeting.watchtowerTopic)+
                (meeting.visit?"\nDiscurso final: "+emptyText(meeting.finalTalkTitle):
                        "\nLector de la Atalaya: "+publicName(meeting.readerId))+
                "\nEvaluación del orador: "+(meeting.evaluated?(meeting.speakerPresented?"Presentó":"No presentó")+
                "\nComentarios: "+emptyText(meeting.evaluationComments):"Pendiente");
        safeDialog().setTitle("Detalle de la reunión").setMessage(text)
                .setPositiveButton("Cerrar",null).show();
    }

    private void showMechanicalProgram(){
        ScrollView page=page("Asignaciones Mecánicas");LinearLayout body=body(page);
        body.addView(label("Mes del registro",14,true));
        Button month=button(selectedMonthLabel(),v->openMonthPicker(()->{showActiveWeekOnly=false;appState.edit().putBoolean("show_active_week_only",false).apply();showMechanicalProgram();}));
        month.setAllCaps(false);month.setCompoundDrawablesWithIntrinsicBounds(0,0,android.R.drawable.ic_menu_my_calendar,0);body.addView(month);
        body.addView(label("Registros independientes para la reunión de entre semana y Reunión Pública y Atalaya, incluso si coinciden en fecha. Solo participan varones autorizados.",13,false));
        if(showActiveWeekOnly){body.addView(label("Semana activa: "+activeWeek,14,true));body.addView(button("Ver mes completo",v->{showActiveWeekOnly=false;appState.edit().putBoolean("show_active_week_only",false).apply();showMechanicalProgram();}));}
        LinearLayout actions=row();actions.addView(button("Registrar asignaciones mecánicas",v->newMechanicalProgram()));actions.addView(button("Configurar reunión entre semana",v->editMeeting()));actions.addView(button("Configurar reunión de fin de semana",v->editPublicMeeting(null,this::showMechanicalProgram)));HorizontalScrollView actionScroll=new HorizontalScrollView(this);actionScroll.addView(actions);body.addView(actionScroll);
        LinearLayout imageActions=row();imageActions.addView(button("Imagen del programa entre semana",v->selectCombinedWeeklyJpg()));imageActions.addView(button("Imagen del programa de fin de semana",v->selectPublicWeeklyJpg()));imageActions.addView(button("PDF mensual de asignaciones",v->selectMechanicalMonthlyPdf()));HorizontalScrollView imageScroll=new HorizontalScrollView(this);imageScroll.addView(imageActions);body.addView(imageScroll);
        List<Models.Meeting> weeks=db.meetings(selectedMonth);
        List<Models.PublicMeeting> weekend=db.publicMeetings(selectedMonth);
        if(showActiveWeekOnly&&!activeWeek.isEmpty())weeks.removeIf(week->!activeWeek.equals(week.date));
        if(showActiveWeekOnly&&!activeWeek.isEmpty())weekend.removeIf(week->!activeWeek.equals(week.date));
        if(weeks.isEmpty()&&weekend.isEmpty()){body.addView(label("No hay reuniones configuradas en este mes. Configure primero la reunión correspondiente.",15,false));return;}
        for(Models.Meeting week:weeks){
            Models.MechanicalProgram program=db.mechanicalProgram(week.date);
            Models.President president=db.president(week.presidentId);
            LinearLayout card=card();
            card.addView(label(week.date+(week.visit?" · VISITA / MARTES":"")+(week.suspended?" · REUNIÓN SUSPENDIDA":""),17,true));
            card.addView(label("Presidente: "+(president==null?"Pendiente":president.name),13,false));
            if(week.suspended){card.addView(label("Sin asignaciones por suspensión.",14,true));body.addView(card);continue;}
            if(program==null)card.addView(label("Asignaciones aún sin registrar.",14,false));
            else{
                for(Models.MechanicalRole role:Models.MechanicalRole.values()){
                    Models.Student assigned=db.student(program.studentId(role));
                    card.addView(label(role.label+": "+(assigned==null?"Pendiente":assigned.name),13,false));
                }
                card.addView(label("Canción inicial: "+emptyText(program.openingSong)+"\nCanción intermedia: "+emptyText(program.middleSong)+"\nCanción final: "+emptyText(program.closingSong),13,false));
            }
            LinearLayout buttons=row();buttons.addView(button(program==null?"Registrar":"Editar",v->editMechanicalProgram(week.date,program)));
            if(program!=null)buttons.addView(button("Eliminar registro",v->confirm("¿Eliminar únicamente las asignaciones mecánicas y las canciones de "+week.date+"? Las otras partes de la reunión se conservarán.",()->{db.deleteMechanicalProgram(week.date);showMechanicalProgram();})));
            HorizontalScrollView horizontal=new HorizontalScrollView(this);horizontal.addView(buttons);card.addView(horizontal);body.addView(card);
        }
        for(Models.PublicMeeting week:weekend){
            Models.MechanicalProgram program=db.mechanicalProgram(week.date,"WEEKEND");
            LinearLayout card=card();card.addView(label(week.date+" · REUNIÓN PÚBLICA Y ATALAYA"+
                    (week.visit?" · VISITA":"")+(week.suspended?" · CANCELADA POR ASAMBLEA":""),17,true));
            card.addView(label("Presidente: "+publicName(week.presidentId),13,false));
            if(week.suspended){card.addView(label("Sin asignaciones mecánicas activas.",14,false));body.addView(card);continue;}
            if(program==null)card.addView(label("Asignaciones pendientes.",14,false));
            else{
                for(Models.MechanicalRole role:Models.MechanicalRole.values()){
                    Models.Student student=db.student(program.studentId(role));
                    card.addView(label(role.label+": "+(student==null?"Pendiente":student.name),13,false));
                }
                card.addView(label("Canción inicial: "+emptyText(program.openingSong)+"\nCanción intermedia: "+emptyText(program.middleSong)+"\nCanción final: "+emptyText(program.closingSong),13,false));
            }
            LinearLayout buttons=row();buttons.addView(button(program==null?"Registrar":"Editar",v->editMechanicalProgram(week.date,program,"WEEKEND")));
            if(program!=null)buttons.addView(button("Eliminar registro",v->confirm("¿Eliminar las asignaciones mecánicas de la reunión del fin de semana "+week.date+"?",()->{db.deleteMechanicalProgram(week.date,"WEEKEND");showMechanicalProgram();})));
            HorizontalScrollView horizontal=new HorizontalScrollView(this);horizontal.addView(buttons);card.addView(horizontal);body.addView(card);
        }
    }

    private void newMechanicalProgram(){
        safeDialog().setTitle("Tipo de reunión").setItems(new String[]{"Entre semana · Escuela y Vida y Ministerio","Fin de semana · Reunión Pública y Atalaya"},
                (dialog,choice)->editMechanicalProgram(null,null,choice==0?"MIDWEEK":"WEEKEND")).setNegativeButton("Cancelar",null).show();
    }

    private void selectMechanicalMonthlyPdf(){
        SharedPreferences report=getSharedPreferences("mechanical_monthly_report",MODE_PRIVATE);
        LinearLayout form=column();form.setPadding(dp(14),dp(6),dp(14),dp(8));
        form.addView(label("Mes: "+selectedMonthLabel(),14,true));
        form.addView(label("Congregación",14,true));EditText congregation=input("Nombre de la congregación",report.getString("congregation","VILLA RICA"));form.addView(congregation);
        form.addView(label("Localidad",14,true));EditText locality=input("Localidad",report.getString("locality","BOCA DEL RÍO"));form.addView(locality);
        form.addView(label("Circuito",14,true));EditText circuit=input("Número o nombre del circuito",report.getString("circuit","25"));form.addView(circuit);
        AlertDialog dialog=safeDialog().setTitle("Reporte mensual de asignaciones mecánicas").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Generar y compartir",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(value(congregation).isEmpty()){toast("Indique el nombre de la congregación.");return;}
            try{
                report.edit().putString("congregation",value(congregation)).putString("locality",value(locality)).putString("circuit",value(circuit)).apply();
                File file=new MechanicalMonthlyReportManager(this,db).monthlyPdf(selectedMonth,value(congregation),value(locality),value(circuit));
                dialog.dismiss();shareFile(file,"application/pdf","Rol mensual de asignaciones mecánicas "+selectedMonth,null);
            }catch(Exception error){toast("No fue posible generar el PDF: "+error.getMessage());}
        }));
        dialog.show();
    }

    private void selectCombinedWeeklyJpg(){
        LinearLayout form=column();form.setPadding(dp(12),0,dp(12),dp(12));
        form.addView(label("Fecha de la semana (la visita se ajusta al martes)",14,true));
        EditText date=dateInput(activeWeek);form.addView(date);
        form.addView(label("Hora de reunión",14,true));
        EditText time=input("Hora de reunión","6:00 p. m.");form.addView(time);
        AlertDialog dialog=safeDialog().setTitle("Imagen del programa integrado")
                .setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Generar y compartir",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(-1).setOnClickListener(v->{
            try{
                String day=db.resolveLifeMinistryDate(value(date));
                File image=new CombinedWeeklyReportManager(this,db).weeklyJpg(day,value(time));
                dialog.dismiss();shareFile(image,"image/jpeg","Programa integrado de la semana "+day,null);
            }catch(Exception error){toast("No fue posible generar la imagen: "+error.getMessage());}
        }));
        dialog.show();
    }

    private void selectPublicWeeklyJpg(){
        String initial=activeWeek;
        if(initial.isEmpty()||db.publicMeeting(initial)==null){
            List<Models.PublicMeeting> meetings=db.publicMeetings(selectedMonth);
            initial=meetings.isEmpty()?YearMonth.parse(selectedMonth).atDay(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).toString():meetings.get(0).date;
        }
        LinearLayout form=column();form.setPadding(dp(12),0,dp(12),dp(12));
        form.addView(label("Fecha de la Reunión Pública y Atalaya",14,true));
        EditText date=dateInput(initial);form.addView(date);
        form.addView(label("Hora de reunión",14,true));
        EditText time=input("Hora de reunión","10:00 a. m.");form.addView(time);
        AlertDialog dialog=safeDialog().setTitle("Imagen del programa de fin de semana")
                .setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Generar y compartir",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(-1).setOnClickListener(v->{
            try{
                String day=LocalDate.parse(value(date)).toString();
                File image=new PublicWeeklyReportManager(this,db).weeklyJpg(day,value(time));
                dialog.dismiss();shareFile(image,"image/jpeg","Programa de Reunión Pública y Atalaya "+day,null);
            }catch(Exception error){toast("No fue posible generar la imagen: "+error.getMessage());}
        }));
        dialog.show();
    }

    private void editMechanicalProgram(String selectedDate,Models.MechanicalProgram existing){editMechanicalProgram(selectedDate,existing,"MIDWEEK");}
    private void editMechanicalProgram(String selectedDate,Models.MechanicalProgram existing,String meetingType){
        Models.MechanicalProgram program=existing==null?new Models.MechanicalProgram():existing;program.meetingType=meetingType;
        LinearLayout form=column();form.setPadding(dp(12),0,dp(12),dp(12));
        String initial=selectedDate==null?(activeWeek.isEmpty()?YearMonth.parse(selectedMonth).atDay(1).toString():activeWeek):selectedDate;
        EditText date=dateInput(initial);form.addView(label("Fecha de la reunión",14,true));form.addView(date);
        if(existing!=null)date.setEnabled(false);
        List<Models.Student> activeMen=db.students(false);activeMen.removeIf(s->!s.isMale());
        EnumMap<Models.MechanicalRole,Spinner> pickers=new EnumMap<>(Models.MechanicalRole.class);
        form.addView(label("Responsables · "+("WEEKEND".equals(meetingType)?"Reunión Pública y Atalaya":"reunión entre semana"),15,true));
        for(Models.MechanicalRole role:Models.MechanicalRole.values()){
            List<Models.Student> candidates=new ArrayList<>();Models.Student none=new Models.Student();none.name="Sin asignar";candidates.add(none);
            for(Models.Student student:activeMen)if(role.authorized(student))candidates.add(student);
            form.addView(label(role.label,14,true));Spinner picker=spinner(candidates);selectById(picker,candidates,program.studentId(role));form.addView(picker);pickers.put(role,picker);
            if(candidates.size()==1)form.addView(label("No hay varones activos autorizados para esta función.",12,false));
        }
        form.addView(label("Canciones · seleccione número y título del catálogo",15,true));
        form.addView(label("Canción inicial",14,true));Spinner opening=songSpinner(program.openingSong);form.addView(opening);
        form.addView(label("Canción intermedia",14,true));Spinner middle=songSpinner(program.middleSong);form.addView(middle);
        form.addView(label("Canción final",14,true));Spinner closing=songSpinner(program.closingSong);form.addView(closing);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        AlertDialog dialog=safeDialog().setTitle(existing==null?"Registrar asignaciones mecánicas":"Editar asignaciones mecánicas").setView(scroll).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{LocalDate.parse(value(date));}catch(Exception error){toast("Seleccione una fecha válida.");return;}
            if(existing==null){try{String resolved="WEEKEND".equals(meetingType)?value(date):db.resolveLifeMinistryDate(value(date));if(db.mechanicalProgram(resolved,meetingType)!=null){toast("Ya existe un registro para esta fecha y tipo de reunión. Use Editar.");return;}}catch(Exception error){toast(error.getMessage());return;}}
            program.date=value(date);program.assigned.clear();
            for(Models.MechanicalRole role:Models.MechanicalRole.values())program.assigned.put(role,((Models.Student)pickers.get(role).getSelectedItem()).id);
            program.openingSong=selectedSong(opening);program.middleSong=selectedSong(middle);program.closingSong=selectedSong(closing);
            try{if("WEEKEND".equals(meetingType))db.saveWeekendMechanicalProgram(program);else db.saveMechanicalProgram(program);activeWeek=program.date;selectedMonth=program.date.substring(0,7);showActiveWeekOnly=true;appState.edit().putString("active_week",activeWeek).putBoolean("show_active_week_only",true).apply();dialog.dismiss();showMechanicalProgram();}
            catch(Exception error){toast("No se pudo guardar: "+error.getMessage());}
        }));
        dialog.show();
    }

    private void showLifeMinistryProgram(){
        ScrollView page=page("Programa de Vida y Ministerio");LinearLayout body=body(page);body.addView(label("Mes del programa",14,true));Button monthButton=button(selectedMonthLabel(),v->openMonthPicker(()->{showActiveWeekOnly=false;appState.edit().putBoolean("show_active_week_only",false).apply();showLifeMinistryProgram();}));monthButton.setAllCaps(false);monthButton.setCompoundDrawablesWithIntrinsicBounds(0,0,android.R.drawable.ic_menu_my_calendar,0);monthButton.setCompoundDrawablePadding(dp(8));body.addView(monthButton);body.addView(label("Semana compartida activa: "+(activeWeek.isEmpty()?"sin seleccionar":activeWeek)+(showActiveWeekOnly?" · mostrando solo esta semana":""),14,true));if(showActiveWeekOnly)body.addView(button("Ver mes completo",v->{showActiveWeekOnly=false;appState.edit().putBoolean("show_active_week_only",false).apply();showLifeMinistryProgram();}));
        HorizontalScrollView toolsScroll=new HorizontalScrollView(this);LinearLayout tools=row();tools.addView(button("Configurar semana",v->editMeeting()));tools.addView(button("Eliminar semana",v->selectWeekToDelete()));tools.addView(button("Nueva asignación",v->editLifePart(null)));tools.addView(button("Importar programa",v->openLifeMinistryImporter()));tools.addView(button("Plantilla Excel",v->shareLifeMinistryTemplate()));tools.addView(button("Exportar datos Excel",v->exportLifeMinistryCsv()));tools.addView(button("PDF mensual",v->createLifeMonthlyPdf()));tools.addView(button("PDF semanal",v->selectLifeWeeklyPdf()));tools.addView(button("4 asignaciones/hoja",v->createLifeAssignmentsPdf()));tools.addView(button("Programar avisos",v->scheduleLifeReminders()));toolsScroll.addView(tools);body.addView(toolsScroll);body.addView(label("Este módulo usa la misma fecha y el mismo presidente configurados en Escuela. Solo se programa la Sala Principal.",13,false));
        List<Models.LifePart> parts=db.lifeParts(selectedMonth);List<Models.LifeProgramRow> rows=db.lifeProgram(selectedMonth);
        if(showActiveWeekOnly&&!activeWeek.isEmpty()){parts.removeIf(p->!activeWeek.equals(p.date));rows.removeIf(r->!activeWeek.equals(r.part.date));}
        boolean hasVisibleVisit=showActiveWeekOnly&&!activeWeek.isEmpty()?db.meeting(activeWeek).visit:db.meetings(selectedMonth).stream().anyMatch(m->m.visit);
        if(parts.isEmpty()&&!hasVisibleVisit){body.addView(label(showActiveWeekOnly?"No hay asignaciones capturadas para la semana "+activeWeek+".":"Importe la plantilla o capture las asignaciones del programa.",16,false));return;}
        String previous="";
        for(Models.LifePart part:parts){
            if(!part.date.equals(previous)){
                if(!previous.isEmpty())addServiceTalkCard(body,db.meeting(previous));
                Models.Meeting meeting=db.meeting(part.date);Models.President president=db.president(meeting.presidentId);
                TextView date=label(part.date+" · Presidente: "+(president==null?"Pendiente":president.name)+(meeting.suspended?" · ASAMBLEA / SUSPENDIDA":"")+(meeting.visit?" · VISITA / MARTES":""),16,true);
                date.setTextColor(Color.WHITE);date.setBackgroundColor(palette("date","#0D6EFD"));date.setPadding(dp(10),dp(8),dp(10),dp(8));body.addView(date);previous=part.date;
            }
            Models.Meeting meeting=db.meeting(part.date);Models.LifeProgramRow assigned=findLifeRow(rows,part.id);LinearLayout card=card();card.addView(label(part.number+". "+part.assignmentName+" ("+part.minutes+" min.)",17,true));card.addView(label("Tema: "+(part.topic.isEmpty()?"—":part.topic),13,false));if(meeting.suspended)card.addView(label("Sin participante: reunión suspendida.",14,true));else addLifeAssignmentLine(card,part,assigned);LinearLayout edit=row();edit.addView(button("Editar programa",v->editLifePart(part)));edit.addView(button("Eliminar programa",v->confirm("¿Eliminar esta asignación del programa y su evaluación?",()->{if(assigned!=null)ReminderScheduler.cancelLife(this,assigned.assignment.id);db.deleteLifePart(part.id);showLifeMinistryProgram();})));card.addView(edit);body.addView(card);
        }
        if(!previous.isEmpty())addServiceTalkCard(body,db.meeting(previous));
        else if(showActiveWeekOnly&&!activeWeek.isEmpty()){Models.Meeting meeting=db.meeting(activeWeek);body.addView(label(activeWeek+" · VISITA / MARTES",16,true));addServiceTalkCard(body,meeting);}
        if(!showActiveWeekOnly)for(Models.Meeting meeting:db.meetings(selectedMonth))if(meeting.visit&&parts.stream().noneMatch(p->p.date.equals(meeting.date))){body.addView(label(meeting.date+" · VISITA / MARTES",16,true));addServiceTalkCard(body,meeting);}
    }

    private void addServiceTalkCard(LinearLayout body,Models.Meeting meeting){if(!meeting.visit||meeting.suspended)return;LinearLayout speech=card();speech.addView(label("Discurso de servicio del superintendente de circuito",17,true));speech.addView(label("Superintendente: "+emptyText(meeting.circuitOverseer)+"\nTema: "+emptyText(meeting.serviceTalkTopic)+"\nCanción: "+emptyText(meeting.serviceSong),14,false));speech.addView(label("Sustituye al Estudio del Libro durante la visita.",13,false));body.addView(speech);}

    private Models.LifeProgramRow findLifeRow(List<Models.LifeProgramRow> rows,long partId){for(Models.LifeProgramRow row:rows)if(row.part.id==partId)return row;return null;}

    private void addLifeAssignmentLine(LinearLayout parent,Models.LifePart part,Models.LifeProgramRow assigned){String names=assigned==null?"Pendiente":assigned.student.name+(assigned.reader==null?"":" · Lector: "+assigned.reader.name);parent.addView(label("Sala Principal: "+names,14,assigned!=null));LinearLayout actions=row();actions.addView(button(assigned==null?"Asignar":"Cambiar",v->editLifeAssignment(part,assigned)));if(assigned!=null){actions.addView(button("JPG participante",v->createAndShareLifeJpg(assigned,false)));if(assigned.reader!=null)actions.addView(button("JPG lector",v->createAndShareLifeJpg(assigned,true)));actions.addView(button("Quitar",v->confirm("¿Quitar a los participantes?",()->{ReminderScheduler.cancelLife(this,assigned.assignment.id);db.deleteLifeAssignment(assigned.assignment.id);showLifeMinistryProgram();})));actions.addView(button("Evaluación",v->editLifeEvaluation(assigned)));}HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.addView(actions);parent.addView(scroll);}

    private void editLifePart(Models.LifePart current){Models.LifePart part=current==null?new Models.LifePart():current;LinearLayout form=column();form.setPadding(dp(12),0,dp(12),0);String initial=current==null?(activeWeek.isEmpty()?LocalDate.now().toString():activeWeek):part.date;EditText date=dateInput(initial);EditText number=input("Número de asignación",current==null?"":String.valueOf(part.number));number.setInputType(InputType.TYPE_CLASS_NUMBER);EditText name=input("Nombre de la asignación",current==null?"":part.assignmentName);EditText minutes=input("Tiempo en minutos",current==null?"":String.valueOf(part.minutes));minutes.setInputType(InputType.TYPE_CLASS_NUMBER);EditText topic=input("Tema",current==null?"":part.topic);topic.setSingleLine(false);topic.setMinLines(2);form.addView(date);form.addView(number);form.addView(name);form.addView(minutes);form.addView(topic);ScrollView scroll=new ScrollView(this);scroll.addView(form);AlertDialog dialog=safeDialog().setTitle(current==null?"Nueva asignación del programa":"Editar asignación del programa").setView(scroll).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{try{LocalDate.parse(value(date));}catch(Exception error){toast("Seleccione una fecha válida.");return;}if(intValue(number,0)<1||intValue(minutes,0)<1||value(name).isEmpty()){toast("Complete número, nombre y tiempo.");return;}part.date=value(date);part.number=intValue(number,0);part.assignmentName=value(name);part.minutes=intValue(minutes,0);part.topic=value(topic);try{db.saveLifePart(part);selectedMonth=part.date.substring(0,7);dialog.dismiss();showLifeMinistryProgram();}catch(Exception error){toast(error.getMessage());}}));dialog.show();}

    private List<Models.Student> lifeStudents(boolean bookConductor,boolean bookReader){List<Models.Student> result=db.students(false);result.removeIf(s->bookReader?!s.lectorEstudioLibro:(!s.vidaMinisterio||(bookConductor&&!s.estudioLibro)));return result;}

    private void editLifeAssignment(Models.LifePart part,Models.LifeProgramRow existing){boolean book=part.isBookStudy();List<Models.Student> participants=lifeStudents(book,false);List<Models.Student> readers=book?lifeStudents(false,true):new ArrayList<>();if(participants.isEmpty()){toast(book?"No hay conductores activos con Vida y Ministerio y Estudio del Libro.":"No hay alumnos activos con la clasificación Vida y Ministerio.");return;}if(book&&readers.isEmpty()){toast("No hay lectores activos con la clasificación Lector del Estudio del Libro.");return;}LinearLayout form=column();form.setPadding(dp(12),0,dp(12),0);form.addView(label(part.toString()+"\nSala Principal",15,true));form.addView(label(book?"Conductor":"Participante",14,true));Spinner participant=spinner(participants);form.addView(participant);Spinner reader=null;if(book){form.addView(label("Lector del Estudio del Libro",14,true));reader=spinner(readers);form.addView(reader);}if(existing!=null){selectById(participant,participants,existing.assignment.studentId);if(reader!=null)selectById(reader,readers,existing.assignment.readerId);}Spinner finalReader=reader;AlertDialog dialog=safeDialog().setTitle(existing==null?"Asignar participación":"Modificar participación").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{Models.LifeAssignment assignment=existing==null?new Models.LifeAssignment():existing.assignment;assignment.partId=part.id;assignment.studentId=((Models.Student)participant.getSelectedItem()).id;assignment.readerId=finalReader==null?0:((Models.Student)finalReader.getSelectedItem()).id;String error=db.validateLifeAssignment(assignment);if(error!=null){toast(error);return;}try{assignment.id=db.saveLifeAssignment(assignment);Models.LifeProgramRow row=db.lifeProgramRow(assignment.id);ReminderScheduler.scheduleLife(this,row,false);ReminderScheduler.scheduleLife(this,row,true);dialog.dismiss();showLifeMinistryProgram();}catch(Exception errorSave){toast(errorSave.getMessage());}}));dialog.show();}

    private void editLifeEvaluation(Models.LifeProgramRow row){Models.LifeAssignment assignment=row.assignment;LinearLayout form=column();form.setPadding(dp(12),0,dp(12),dp(12));form.addView(label(row.part.number+". "+row.part.assignmentName+"\n"+row.student.name+" · Sala Principal\nTiempo asignado: "+row.part.minutes+" min.",15,true));TextView clock=label(formatSeconds(assignment.actualSeconds),30,true);clock.setGravity(Gravity.CENTER);form.addView(clock);LinearLayout timer=row();Button start=button("Iniciar",null),pause=button("Pausar",null),reset=button("Reiniciar",null);timer.addView(start);timer.addView(pause);timer.addView(reset);form.addView(timer);CheckBox presented=checkBox("El participante presentó la asignación",!assignment.evaluated||assignment.studentPresented);EditText reason=input("Causa por la que no presentó",assignment.studentAbsenceReason);List<Models.Student> replacements=new ArrayList<>();Models.Student none=new Models.Student();none.name="Sin sustituto";replacements.add(none);replacements.addAll(lifeStudents(row.part.isBookStudy(),false));Spinner replacement=spinner(replacements);selectById(replacement,replacements,assignment.replacementStudentId);TextView replacementLabel=label("Quién presentó en su lugar",14,true);form.addView(presented);form.addView(reason);form.addView(replacementLabel);form.addView(replacement);CheckBox readerPresented=checkBox("El lector se presentó",!assignment.evaluated||assignment.readerPresented);EditText readerReason=input("Causa de ausencia del lector",assignment.readerAbsenceReason);if(row.reader!=null){form.addView(readerPresented);form.addView(readerReason);}CheckBox adhered=checkBox("Se apegó al tema asignado",assignment.topicAdhered);EditText comments=input("Comentarios de la presentación",assignment.evaluationComments);comments.setSingleLine(false);comments.setMinLines(3);form.addView(label("Tema asignado: "+emptyText(row.part.topic),13,false));form.addView(adhered);form.addView(comments);Runnable visibility=()->{int show=presented.isChecked()?View.GONE:View.VISIBLE;reason.setVisibility(show);replacementLabel.setVisibility(show);replacement.setVisibility(show);if(row.reader!=null)readerReason.setVisibility(readerPresented.isChecked()?View.GONE:View.VISIBLE);};presented.setOnCheckedChangeListener((b,c)->visibility.run());readerPresented.setOnCheckedChangeListener((b,c)->visibility.run());visibility.run();final Handler handler=new Handler(Looper.getMainLooper());final long[] elapsed={Math.max(0,assignment.actualSeconds)*1000L},started={0};final boolean[] running={false};Runnable ticker=new Runnable(){@Override public void run(){long total=elapsed[0]+(running[0]?SystemClock.elapsedRealtime()-started[0]:0);clock.setText(formatSeconds((int)(total/1000)));if(running[0])handler.postDelayed(this,200);}};start.setOnClickListener(v->{if(!running[0]){running[0]=true;started[0]=SystemClock.elapsedRealtime();handler.post(ticker);}});pause.setOnClickListener(v->{if(running[0]){elapsed[0]+=SystemClock.elapsedRealtime()-started[0];running[0]=false;handler.removeCallbacks(ticker);clock.setText(formatSeconds((int)(elapsed[0]/1000)));}});reset.setOnClickListener(v->{running[0]=false;handler.removeCallbacks(ticker);elapsed[0]=0;clock.setText("00:00");});ScrollView scroll=new ScrollView(this);scroll.addView(form);AlertDialog dialog=safeDialog().setTitle("Evaluación de Vida y Ministerio").setView(scroll).setNegativeButton("Cancelar",null).setPositiveButton("Finalizar y guardar",null).create();dialog.setOnDismissListener(x->handler.removeCallbacks(ticker));dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{if(running[0]){elapsed[0]+=SystemClock.elapsedRealtime()-started[0];running[0]=false;handler.removeCallbacks(ticker);}if(!presented.isChecked()&&value(reason).isEmpty()){toast("Indique la causa por la que no presentó.");return;}if(row.reader!=null&&!readerPresented.isChecked()&&value(readerReason).isEmpty()){toast("Indique la causa de ausencia del lector.");return;}assignment.actualSeconds=(int)(elapsed[0]/1000);assignment.studentPresented=presented.isChecked();assignment.studentAbsenceReason=value(reason);assignment.replacementStudentId=assignment.studentPresented?0:((Models.Student)replacement.getSelectedItem()).id;assignment.readerPresented=row.reader==null||readerPresented.isChecked();assignment.readerAbsenceReason=row.reader==null?"":value(readerReason);assignment.topicAdhered=adhered.isChecked();assignment.evaluationComments=value(comments);assignment.evaluated=true;db.saveLifeEvaluation(assignment);dialog.dismiss();showLifeMinistryProgram();}));dialog.show();}

    private void showLifeMinistryHistory(){ScrollView page=page("Historial de Vida y Ministerio");LinearLayout body=body(page);List<Models.Student> students=db.students(true);students.removeIf(s->!s.vidaMinisterio&&!s.lectorEstudioLibro);if(students.isEmpty()){body.addView(label("No hay alumnos clasificados para este módulo.",16,false));return;}Spinner student=spinner(students);EditText consultation=dateInput(LocalDate.now().toString());body.addView(label("Alumno",14,true));body.addView(student);body.addView(label("Fecha de la consulta",14,true));body.addView(consultation);LinearLayout results=column();body.addView(button("Consultar",v->renderLifeHistory((Models.Student)student.getSelectedItem(),value(consultation),results)));body.addView(results);renderLifeHistory(students.get(0),value(consultation),results);}

    private void renderLifeHistory(Models.Student student,String consultationDate,LinearLayout target){LocalDate consultation;try{consultation=LocalDate.parse(consultationDate);}catch(Exception error){toast("Seleccione una fecha válida.");return;}target.removeAllViews();List<Models.LifeProgramRow> history=db.lifeHistory(student.id);String last=null;List<Models.LifeProgramRow> future=new ArrayList<>();for(Models.LifeProgramRow row:history){LocalDate date=LocalDate.parse(row.part.date);if(date.isAfter(consultation))future.add(row);else if(last==null)last=row.part.date;}target.addView(label("Última asignación hasta "+consultationDate+"\nSala Principal: "+elapsed(last,consultation),15,true));future.sort((a,b)->a.part.date.compareTo(b.part.date));StringBuilder upcoming=new StringBuilder("Asignaciones futuras:");if(future.isEmpty())upcoming.append(" sin asignaciones programadas");else for(Models.LifeProgramRow row:future)upcoming.append("\n").append(row.part.date).append(" · ").append(row.part.assignmentName).append(" · ").append(row.assignment.readerId==student.id?"Lector":"Participante").append(" (faltan ").append(ChronoUnit.DAYS.between(consultation,LocalDate.parse(row.part.date))).append(" días)");target.addView(label(upcoming.toString(),14,true));if(history.isEmpty())target.addView(label("No hay actividad registrada.",15,false));for(Models.LifeProgramRow row:history){boolean reader=row.assignment.readerId==student.id;LinearLayout card=card();card.addView(label(row.part.date+" · Sala Principal",15,true));card.addView(label(row.part.assignmentName+" · "+(reader?"Lector del Estudio del Libro":"Participante")+"\nTema: "+emptyText(row.part.topic)+"\nPulse para ver la evaluación",13,false));card.setClickable(true);card.setFocusable(true);card.setOnClickListener(v->showLifeHistoryEvaluation(student,row));target.addView(card);}}

    private void showLifeHistoryEvaluation(Models.Student consulted,Models.LifeProgramRow row){boolean reader=row.assignment.readerId==consulted.id;StringBuilder message=new StringBuilder("Fecha: ").append(row.part.date).append("\nSala: Principal\nParticipación consultada: ").append(reader?"Lector del Estudio del Libro":"Participante").append("\nAsignación: ").append(row.part.assignmentName).append("\nTema: ").append(emptyText(row.part.topic));if(!row.assignment.evaluated)message.append("\n\nTodavía no se ha registrado una evaluación.");else{Models.Student replacement=db.student(row.assignment.replacementStudentId);message.append("\n\nTiempo asignado: ").append(row.part.minutes).append(" min.\nTiempo realizado: ").append(formatSeconds(row.assignment.actualSeconds)).append("\n\nParticipante: ").append(row.student.name).append("\nSe presentó: ").append(row.assignment.studentPresented?"Sí":"No");if(!row.assignment.studentPresented)message.append("\nCausa: ").append(emptyText(row.assignment.studentAbsenceReason)).append("\nSustituto: ").append(replacement==null?"Sin sustituto":replacement.name);if(row.reader!=null){message.append("\n\nLector: ").append(row.reader.name).append("\nSe presentó: ").append(row.assignment.readerPresented?"Sí":"No");if(!row.assignment.readerPresented)message.append("\nCausa: ").append(emptyText(row.assignment.readerAbsenceReason));}message.append("\n\nApego al tema: ").append(row.assignment.topicAdhered?"Sí":"No").append("\nComentarios: ").append(emptyText(row.assignment.evaluationComments));}safeDialog().setTitle("Evaluación de la asignación").setMessage(message.toString()).setPositiveButton("Cerrar",null).show();}

    private void openLifeMinistryImporter(){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(EXCEL_MIME);intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{EXCEL_MIME,"application/vnd.ms-excel"});try{startActivityForResult(intent,REQUEST_IMPORT_LIFE_MINISTRY);}catch(ActivityNotFoundException error){toast("No se encontró un selector de archivos.");}}
    private void shareLifeMinistryTemplate(){File directory=new File(getCacheDir(),"reportes");if(!directory.exists()&&!directory.mkdirs()){toast("No fue posible preparar la plantilla.");return;}File target=new File(directory,"Plantilla_Vida_y_Ministerio.xlsx");try(InputStream input=getAssets().open("Plantilla_Vida_y_Ministerio.xlsx");FileOutputStream output=new FileOutputStream(target)){byte[] buffer=new byte[8192];int read;while((read=input.read(buffer))!=-1)output.write(buffer,0,read);shareFile(target,EXCEL_MIME,"Plantilla del programa Vida y Ministerio",null);}catch(Exception error){toast("No fue posible compartir la plantilla: "+error.getMessage());}}
    private void exportLifeMinistryCsv(){
        try{
            File directory=new File(getCacheDir(),"reportes");
            if(!directory.exists()&&!directory.mkdirs())throw new IllegalStateException("No fue posible preparar la carpeta de exportación.");
            File target=new File(directory,"Vida_y_Ministerio_"+selectedMonth+".csv");
            try(java.io.OutputStream output=new java.io.FileOutputStream(target);
                java.io.OutputStreamWriter writer=new java.io.OutputStreamWriter(output,java.nio.charset.StandardCharsets.UTF_8)){
                writer.write("\\uFEFFFecha;Número de asignación;Nombre de la asignación;Tiempo (minutos);Tema\\r\\n");
                for(Models.LifePart part:db.lifeParts(selectedMonth)){
                    writer.write(csvCell(part.date)+";"+part.number+";"+csvCell(part.assignmentName)+";"+part.minutes+";"+csvCell(part.topic)+"\\r\\n");
                }
                writer.flush();
            }
            shareFile(target,"text/csv","Datos de Vida y Ministerio "+selectedMonth,null);
        }catch(Exception error){safeDialog().setTitle("No se pudo exportar Vida y Ministerio").setMessage(error.getMessage()==null?"Error al generar el archivo.":error.getMessage()).setPositiveButton("Aceptar",null).show();}
    }
    private String csvCell(String value) {
        String text = value == null ? "" : value;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
    private void createLifeMonthlyPdf(){try{File file=new LifeMinistryReportManager(this,db).monthlyPdf(selectedMonth);shareFile(file,"application/pdf","Programa Vida y Ministerio "+selectedMonth,null);}catch(Exception error){toast("No fue posible generar el PDF: "+error.getMessage());}}
    private void selectLifeWeeklyPdf(){LinearLayout form=column();EditText date=dateInput(activeWeek);form.addView(label("Semana del reporte · Sala Principal",14,true));form.addView(date);AlertDialog dialog=safeDialog().setTitle("PDF semanal de Vida y Ministerio").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Generar",null).create();dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{String day=value(date);try{day=db.resolveLifeMinistryDate(day);File file=new LifeMinistryReportManager(this,db).weeklyPdf(day);dialog.dismiss();shareFile(file,"application/pdf","Programa semanal Vida y Ministerio "+day,null);}catch(Exception error){toast("No fue posible generar el PDF: "+error.getMessage());}}));dialog.show();}
    private void createLifeAssignmentsPdf(){try{File file=new LifeMinistryReportManager(this,db).assignmentPdf(selectedMonth);shareFile(file,"application/pdf","Asignaciones Vida y Ministerio "+selectedMonth,null);}catch(Exception error){toast("No fue posible generar el PDF: "+error.getMessage());}}
    private void createAndShareLifeJpg(Models.LifeProgramRow row,boolean reader){try{File file=new LifeMinistryReportManager(this,db).assignmentJpg(row,reader);String president=row.president==null?"el presidente de la reunión":row.president.name;String role=reader?"como lector del Estudio del Libro":"en «"+row.part.assignmentName+"»";String message="Recordatorio: tienes una participación "+role+" el "+row.part.date+" en Sala Principal. Si no podrás participar o necesitas un cambio, informa a "+president+".";shareFile(file,"image/jpeg",message,"com.whatsapp");}catch(Exception error){toast("No fue posible generar la imagen: "+error.getMessage());}}
    private void scheduleLifeReminders(){List<Models.LifeProgramRow> rows=db.lifeProgram(selectedMonth);for(Models.LifeProgramRow row:rows){ReminderScheduler.scheduleLife(this,row,false);ReminderScheduler.scheduleLife(this,row,true);}toast(rows.size()+" asignaciones revisadas. Se avisará siete días antes a las 9:00.");}

    private void showSettings(){
        ScrollView page=page("Paleta de banners y textos");LinearLayout body=body(page);body.addView(label("Toque cada muestra y elija el color deseado de la paleta. Los cambios se aplican a la aplicación y a los reportes.",14,false));
        String[][] specs={{"title","Banner principal","#00B050"},{"date","Banner de fecha","#0D6EFD"},{"assignment","Cabecera Asignación","#BFBFBF"},{"main","Cabecera Sala Principal","#ED7D31"},{"aux","Cabecera Sala Auxiliar","#FFC000"},{"source","Cabecera Fuente","#955FEF"},{"text","Texto de la aplicación","#1F2937"}};Map<String,String> choices=new LinkedHashMap<>();
        for(String[] spec:specs){String current=palette.getString(spec[0],spec[2]);choices.put(spec[0],current);LinearLayout option=row();option.setGravity(Gravity.CENTER_VERTICAL);option.addView(label(spec[1],14,true),new LinearLayout.LayoutParams(0,-2,1));Button preview=button(current,null);styleColorButton(preview,current);preview.setOnClickListener(v->openColorPalette(spec[1],choices,spec[0],preview));option.addView(preview,new LinearLayout.LayoutParams(dp(126),dp(50)));body.addView(option);body.addView(divider());}
        body.addView(button("Guardar paleta",v->{SharedPreferences.Editor edit=palette.edit();for(Map.Entry<String,String> e:choices.entrySet())edit.putString(e.getKey(),e.getValue());edit.apply();toast("Paleta guardada.");rebuildCurrentShell();showSettings();}));
        body.addView(button("Restablecer colores del formato",v->{palette.edit().clear().apply();rebuildCurrentShell();showSettings();}));
    }

    private EditText passwordInput(String hint){
        EditText field=input(hint,"");
        field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return field;
    }

    private char[] password(EditText field){return field.getText().toString().toCharArray();}

    private void showSupportLogin(){
        LinearLayout form=column();form.setPadding(dp(14),dp(8),dp(14),dp(8));
        EditText name=input("Usuario","");EditText secret=passwordInput("Contraseña");
        form.addView(label("Acceso a SOPORTE",18,true));form.addView(name);form.addView(secret);
        AlertDialog dialog=safeDialog().setTitle("Ingreso protegido").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Ingresar",null).create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{char[] entered=password(secret);
            try{
                SupportAuth.User user=new SupportAuth(db).login(value(name),entered);
                if(user==null){toast("Usuario o contraseña incorrectos.");return;}
                secret.setText("");dialog.dismiss();
                if(user.mustChange)forceSupportPasswordChange(user);
                else{supportUser=user;showSupportPanel();}
            }catch(Exception error){toast(error.getMessage());}finally{Arrays.fill(entered,'\0');}
        }));dialog.show();
    }

    private void forceSupportPasswordChange(SupportAuth.User user){
        LinearLayout form=column();form.setPadding(dp(14),dp(6),dp(14),dp(8));
        form.addView(label("La contraseña inicial o temporal dejará de funcionar al establecer una nueva. Elija una contraseña de al menos 8 caracteres.",14,false));
        EditText fresh=passwordInput("Nueva contraseña"),repeat=passwordInput("Confirmar contraseña");form.addView(fresh);form.addView(repeat);
        AlertDialog dialog=safeDialog().setTitle("Cambio obligatorio de contraseña").setView(form).setNegativeButton("Salir",(d,w)->{supportUser=null;showHome();}).setPositiveButton("Guardar y entrar",null).create();
        dialog.setCanceledOnTouchOutside(false);dialog.setOnCancelListener(d->{supportUser=null;showHome();});
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{char[] first=password(fresh),second=password(repeat);
            try{
                if(!Arrays.equals(first,second)){toast("Las contraseñas no coinciden.");return;}
                new SupportAuth(db).setPassword(user.username,first,false);
                fresh.setText("");repeat.setText("");supportUser=new SupportAuth.User(user.username,false);dialog.dismiss();showSupportPanel();
            }catch(Exception error){toast(error.getMessage());}finally{Arrays.fill(first,'\0');Arrays.fill(second,'\0');}
        }));dialog.show();
    }

    private void buildSupportShell(){
        inModule=true;moduleMode="SUPPORT";
        LinearLayout root=column();root.setBackgroundColor(Color.rgb(242,247,251));
        TextView banner=label("SOPORTE · acceso autorizado",21,true);banner.setTextColor(Color.WHITE);banner.setBackgroundColor(Color.rgb(7,38,62));banner.setPadding(dp(18),dp(15),dp(18),dp(15));root.addView(banner);
        LinearLayout navigation=row();navigation.addView(navButton("Inicio",v->showHome()));navigation.addView(navButton("Cerrar sesión",v->showHome()));root.addView(navigation);
        content=new FrameLayout(this);root.addView(content,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }

    private void showSupportPanel(){
        if(supportUser==null)return;
        buildSupportShell();ScrollView screen=page("SOPORTE");LinearLayout body=body(screen);
        body.addView(label("Usuario: "+supportUser.username+". Este espacio solo se abre al tocar el logotipo AAPRC y validar el acceso.",14,false));
        body.addView(button("Crear respaldo cifrado",v->promptSupportBackup()));
        body.addView(button("Restaurar desde un archivo",v->openSupportRestorePicker()));
        body.addView(button("Respaldos internos",v->showInternalBackups()));
        body.addView(button("Comprobar integridad y registros",v->showSupportHealth()));
        body.addView(button("Reconstruir avisos pendientes",v->rebuildAllReminders()));
        body.addView(button("Gestionar usuarios y contraseñas",v->showSupportUsers()));
        body.addView(label("Guarde el archivo cifrado y su clave de respaldo en lugares seguros. Las contraseñas de SOPORTE se conservan al restaurar para que un respaldo antiguo no reactive admin/root.",13,false));
    }

    private void promptSupportBackup(){
        if(supportUser==null)return;
        LinearLayout form=column();form.setPadding(dp(14),dp(6),dp(14),dp(8));
        form.addView(label("Esta clave protege el archivo de respaldo y será necesaria para restaurarlo. No se guarda en la aplicación.",14,false));
        EditText pass=passwordInput("Clave del respaldo"),repeat=passwordInput("Confirmar clave");form.addView(pass);form.addView(repeat);
        AlertDialog dialog=safeDialog().setTitle("Respaldo completo de la base de datos").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Crear y compartir",null).create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{char[] first=password(pass),second=password(repeat);
            try{
                if(!Arrays.equals(first,second)){toast("Las claves no coinciden.");return;}
                File file=new SupportMaintenance(this,db).encryptedBackup(first);pass.setText("");repeat.setText("");dialog.dismiss();
                pendingSupportBackup=file;appState.edit().putString("pending_support_backup",file.getAbsolutePath()).apply();
                Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE,file.getName());
                try{startActivityForResult(save,REQUEST_SUPPORT_SAVE);}catch(ActivityNotFoundException error){pendingSupportBackup=null;appState.edit().remove("pending_support_backup").apply();shareFile(file,"application/octet-stream","Respaldo cifrado completo AAPRC. Conserve también la clave de respaldo.",null);}
            }catch(Exception error){toast("No se pudo respaldar: "+error.getMessage());}finally{Arrays.fill(first,'\0');Arrays.fill(second,'\0');}
        }));dialog.show();
    }

    private void openSupportRestorePicker(){
        if(supportUser==null)return;
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        try{startActivityForResult(intent,REQUEST_SUPPORT_RESTORE);}catch(ActivityNotFoundException error){toast("No se encontró un selector de archivos.");}
    }

    private void confirmSupportRestore(Uri uri){
        LinearLayout form=column();form.setPadding(dp(14),dp(6),dp(14),dp(8));
        form.addView(label("Se sustituirán todos los datos de los módulos. Se verificará el archivo y se guardará un respaldo interno previo. Para archivos .aaprc indique su clave; las antiguas bases .db no llevan clave.",14,false));
        EditText secret=passwordInput("Clave del respaldo (si tiene)");form.addView(secret);
        AlertDialog dialog=safeDialog().setTitle("Restaurar base de datos").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Restaurar ahora",null).create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{char[] entered=password(secret);
            try(InputStream input=getContentResolver().openInputStream(uri)){
                if(input==null)throw new IllegalArgumentException("No fue posible abrir el archivo.");
                restoreSupportData(input,entered);dialog.dismiss();
            }catch(Exception error){toast("No se pudo restaurar: "+error.getMessage());}
            finally{secret.setText("");Arrays.fill(entered,'\0');}
        }));dialog.show();
    }

    private void showInternalBackups(){
        if(supportUser==null)return;
        List<File> backups=new SupportMaintenance(this,db).localBackups();
        if(backups.isEmpty()){toast("No hay respaldos internos todavía.");return;}
        List<String> names=new ArrayList<>();for(File file:backups)names.add(file.getName());
        LinearLayout form=column();form.setPadding(dp(14),dp(6),dp(14),dp(8));form.addView(label("Elija una copia interna. También se hará un respaldo previo de la base actual.",14,false));Spinner options=spinner(names);form.addView(options);
        AlertDialog dialog=safeDialog().setTitle("Restaurar respaldo interno").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Restaurar ahora",null).create();dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{File selected=backups.get(options.getSelectedItemPosition());
            try(InputStream input=new java.io.FileInputStream(selected)){restoreSupportData(input,new char[0]);dialog.dismiss();}
            catch(Exception error){toast("No se pudo restaurar: "+error.getMessage());}
        }));dialog.show();
    }

    private void restoreSupportData(InputStream input,char[] pass) throws Exception{
        if(supportUser==null)throw new IllegalStateException("Vuelva a iniciar sesión en SOPORTE.");
        List<Models.ProgramRow> oldSchool=db.program("____-__");List<Models.LifeProgramRow> oldLife=db.lifeProgram("____-__");
        SupportMaintenance.Restored result;
        try{result=new SupportMaintenance(this,db).restore(input,pass);}
        catch(Exception error){db=new MinisterioDb(this);throw error;}
        for(Models.ProgramRow row:oldSchool)ReminderScheduler.cancel(this,row.movement.id);
        for(Models.LifeProgramRow row:oldLife)ReminderScheduler.cancelLife(this,row.assignment.id);
        db=result.database;selectedMonth=YearMonth.now().toString();activeWeek="";showActiveWeekOnly=false;
        appState.edit().remove("active_week").putBoolean("show_active_week_only",false).apply();
        int scheduled=-1;
        try{scheduled=rebuildAllRemindersSilently();}catch(Exception error){toast("Base restaurada, pero los avisos requieren revisión: "+error.getMessage());}
        showHome();toast("Base restaurada. Copia previa: "+result.rollbackName+(scheduled>=0?". Avisos revisados: "+scheduled:". Reconstruya los avisos desde SOPORTE")+". Ingrese de nuevo si lo necesita.");
    }

    private void showSupportHealth(){
        if(supportUser==null)return;
        try{safeDialog().setTitle("Estado de la base de datos").setMessage(new SupportMaintenance(this,db).healthReport()).setPositiveButton("Cerrar",null).show().setCanceledOnTouchOutside(false);}
        catch(Exception error){toast("La comprobación encontró un problema: "+error.getMessage());}
    }

    private int rebuildAllRemindersSilently(){
        List<Models.ProgramRow> school=db.program("____-__");List<Models.LifeProgramRow> life=db.lifeProgram("____-__");
        for(Models.ProgramRow row:school){ReminderScheduler.cancel(this,row.movement.id);ReminderScheduler.schedule(this,row);}
        for(Models.LifeProgramRow row:life){ReminderScheduler.cancelLife(this,row.assignment.id);ReminderScheduler.scheduleLife(this,row,false);ReminderScheduler.scheduleLife(this,row,true);}
        return school.size()+life.size();
    }

    private void rebuildAllReminders(){
        if(supportUser==null)return;
        try{toast(rebuildAllRemindersSilently()+" asignaciones revisadas. Solo se programan avisos todavía pendientes.");}
        catch(Exception error){toast("No se pudieron reconstruir los avisos: "+error.getMessage());}
    }

    private void showSupportUsers(){
        if(supportUser==null)return;
        ScrollView screen=page("Usuarios de SOPORTE");LinearLayout body=body(screen);body.addView(button("Volver a SOPORTE",v->showSupportPanel()));
        body.addView(label("Usuarios registrados: "+String.join(", ",new SupportAuth(db).usernames()),14,false));
        body.addView(button("Dar de alta un usuario",v->addSupportUser()));
        body.addView(button("Modificar nombre de usuario",v->renameSupportUser()));
        body.addView(button("Modificar contraseña de un usuario",v->changeSupportUserPassword()));
        body.addView(label("Cada usuario nuevo deberá cambiar su contraseña temporal en su primer ingreso. Para modificar cualquier cuenta deberá confirmar su propia contraseña.",13,false));
    }

    private void addSupportUser(){
        LinearLayout form=column();form.setPadding(dp(14),dp(6),dp(14),dp(8));EditText name=input("Nuevo usuario","");EditText current=passwordInput("Su contraseña actual"),fresh=passwordInput("Contraseña temporal (mínimo 8 caracteres)"),repeat=passwordInput("Confirmar contraseña temporal");
        form.addView(name);form.addView(current);form.addView(fresh);form.addView(repeat);
        AlertDialog dialog=safeDialog().setTitle("Alta de usuario de SOPORTE").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{char[] old=password(current),first=password(fresh),second=password(repeat);
            try{
                if(supportUser==null||new SupportAuth(db).login(supportUser.username,old)==null){toast("Confirme su contraseña actual.");return;}
                if(!Arrays.equals(first,second)){toast("Las contraseñas no coinciden.");return;}
                new SupportAuth(db).addUser(value(name),first);dialog.dismiss();showSupportUsers();toast("Usuario creado. Deberá cambiar su contraseña en el primer ingreso.");
            }catch(Exception error){toast(error.getMessage());}finally{Arrays.fill(old,'\0');Arrays.fill(first,'\0');Arrays.fill(second,'\0');}
        }));dialog.show();
    }

    private void changeSupportUserPassword(){
        if(supportUser==null)return;
        List<String> users=new SupportAuth(db).usernames();Spinner selected=spinner(users);
        LinearLayout form=column();form.setPadding(dp(14),dp(6),dp(14),dp(8));form.addView(label("Cuenta cuya contraseña se modificará",14,true));form.addView(selected);
        EditText current=passwordInput("Su contraseña actual"),fresh=passwordInput("Nueva contraseña"),repeat=passwordInput("Confirmar nueva contraseña");form.addView(current);form.addView(fresh);form.addView(repeat);
        AlertDialog dialog=safeDialog().setTitle("Modificar contraseña").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{char[] old=password(current),first=password(fresh),second=password(repeat);
            try{
                if(supportUser==null||new SupportAuth(db).login(supportUser.username,old)==null){toast("Confirme su contraseña actual.");return;}
                if(!Arrays.equals(first,second)){toast("Las contraseñas no coinciden.");return;}
                String target=(String)selected.getSelectedItem();boolean another=!target.equals(supportUser.username);
                new SupportAuth(db).setPassword(target,first,another);dialog.dismiss();showSupportUsers();toast(another?"Contraseña temporal creada; el usuario deberá cambiarla al entrar.":"Contraseña actualizada.");
            }catch(Exception error){toast(error.getMessage());}finally{Arrays.fill(old,'\0');Arrays.fill(first,'\0');Arrays.fill(second,'\0');}
        }));dialog.show();
    }

    private void renameSupportUser(){
        if(supportUser==null)return;
        List<String> users=new SupportAuth(db).usernames();Spinner selected=spinner(users);
        LinearLayout form=column();form.setPadding(dp(14),dp(6),dp(14),dp(8));form.addView(label("Usuario que se modificará",14,true));form.addView(selected);
        EditText fresh=input("Nuevo nombre de usuario","");EditText current=passwordInput("Su contraseña actual");form.addView(fresh);form.addView(current);
        AlertDialog dialog=safeDialog().setTitle("Modificar usuario").setView(form).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",null).create();dialog.setCanceledOnTouchOutside(false);
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{char[] old=password(current);
            try{
                if(supportUser==null||new SupportAuth(db).login(supportUser.username,old)==null){toast("Confirme su contraseña actual.");return;}
                String target=(String)selected.getSelectedItem();String changed=new SupportAuth(db).renameUser(target,value(fresh));
                if(target.equals(supportUser.username))supportUser=new SupportAuth.User(changed,false);
                dialog.dismiss();showSupportUsers();toast("Nombre de usuario actualizado.");
            }catch(Exception error){toast(error.getMessage());}finally{Arrays.fill(old,'\0');}
        }));dialog.show();
    }

    private void confirm(String message,Runnable action){safeDialog().setMessage(message).setNegativeButton("Cancelar",null).setPositiveButton("Aceptar",(d,w)->action.run()).show();}
}
