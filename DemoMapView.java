package com.banger.xiaowei.ui.mapbar.resource;
/**
 * 地图主界面中间的自定义MapView
 */

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.PointF;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.banger.xiaowei.R;
import com.banger.xiaowei.adapter.StringAdapter;
import com.banger.xiaowei.bean.CustomResult;
import com.banger.xiaowei.bean.Customer;
import com.banger.xiaowei.bean.CustomerInfo;
import com.banger.xiaowei.provider.webservice.CustomerWebService;
import com.banger.xiaowei.provider.webservice.MapWebService;
import com.banger.xiaowei.ui.BaseActivity;
import com.banger.xiaowei.ui.customer.AddCusActivity;
import com.banger.xiaowei.ui.customer.CDetailActivity;
import com.banger.xiaowei.ui.mapbar.McpMapActivity;
import com.banger.xiaowei.util.Constance;
import com.banger.xiaowei.util.DistanceUitles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mapbar.map.Annotation;
import com.mapbar.map.ArrowOverlay;
import com.mapbar.map.CalloutStyle;
import com.mapbar.map.CustomAnnotation;
import com.mapbar.map.IconOverlay;
import com.mapbar.map.MapRenderer;
import com.mapbar.map.MapState;
import com.mapbar.map.MapView;
import com.mapbar.map.ModelOverlay;
import com.mapbar.map.RouteOverlay;
import com.mapbar.map.Vector2DF;
import com.mapbar.poiquery.ReverseGeocoder;
import com.mapbar.poiquery.ReverseGeocoderDetail;

import org.json.JSONException;
import org.json.JSONObject;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class DemoMapView extends MapView implements ReverseGeocoder.EventHandler {
    private Context mContext;
    private boolean mInited = false;
    public static Handler mHandler;
    private static final int[] mRouteOverlayColors = {0xffaa0000, 0xff00aa00,
            0xff0000aa, 0xff4578fc};
    private ModelOverlay mCarOverlay = null;
    // 电子眼
    private Annotation[] mCameraAnnotations = null;
    // 箭头
    private ArrowOverlay mArrowOverlay = null;
    // 小车状态
    private boolean mIsLockedCar = false;
    private float mCarOriented = 0.0f;
    private Point mCarPosition = null;
    // 地图当前的状态
    private MapState mMapState = null;
    // 需要绘制的路线
    private RouteOverlay[] mRouteCollectionOverlays = null;
    private int mRouteOverlayNumber = 0;
    // 用来绘制放大图
    private ImageView mExpandView = null;
    public Bitmap mBitmap = null;
    // 选中的POI
    private Annotation mPoiAnnotation = null;
    private Annotation mPositionAnnotation = null;

    private MapRenderer mRenderer = null;

    private static final float ZOOM_STEP = 0.5f;
    private Vector2DF mZoomLevelRange = null;
    private static final int BITMAP_WIDTH = 480;
    private static final int BITMAP_HEIGHT = 480;

    public static Point mClickPoint = null;
    private ReverseGeocoder mReverseGeocoder;
    private CustomerInfo myCusInfo = null;
    private int MapType = -1;//1--地图 2--客户地图 3--日程地图
    private CustomAnnotation mCustomAnnotation;
    public static final String PREFERENCE_LNG_KEY = "lng";
    public static final String PREFERENCE_LAT_KEY = "lat";
    private IconOverlay mCarIcon;

    private void init(Context context) {
        mContext = context;
        mIsLockedCar = true;
        mRouteCollectionOverlays = new RouteOverlay[4];
    }

    // TODO: 拋出異常

    public DemoMapView(Context context) {
        super(context);
        init(context);
    }

    public DemoMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public void setData(CustomerInfo info, int type) {
        myCusInfo = info;
        MapType = type;


    }

    public void setZoomHandler(Handler handler) {
        mHandler = handler;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        super.onSurfaceCreated(gl, config);
        // 防止重复创建
        if (!mInited) {

            mRenderer = super.getMapRenderer();
            if (mRenderer == null)
                return;
            mRenderer.setDataUrlPrefix(MapRenderer.UrlType.satellite, Config.MAPURL);
            mRenderer.setWorldCenter(Config.centerPoint);
            mRenderer.setZoomLevel(10f);
            mClickPoint = new Point(mRenderer.getWorldCenter());
            Vector2DF pivot = new Vector2DF(0.5f, 0.82f);
            // 添加点击气泡
            mPoiAnnotation = new Annotation(2, mClickPoint, 1101, pivot);
            mPositionAnnotation = new Annotation(2, mClickPoint, 1101, pivot);
            mPoiAnnotation.setTag(-1);
            mPositionAnnotation.setTag(-1);
            CalloutStyle calloutStyle = mPoiAnnotation.getCalloutStyle();
            calloutStyle.anchor.set(0.5f, 0.0f);
            //calloutStyle.leftIcon = 101;
            calloutStyle.rightIcon = 1001;
            mPoiAnnotation.setCalloutStyle(calloutStyle);
            mPositionAnnotation.setTitle("");
            mPositionAnnotation.setCalloutStyle(calloutStyle);
            mRenderer.addAnnotation(mPoiAnnotation);
            mRenderer.addAnnotation(mPositionAnnotation);
            mReverseGeocoder = new ReverseGeocoder(this);
            mReverseGeocoder.setMode(ReverseGeocoder.Mode.online);
            //创建完毕通知
            if (mHandler != null) {
                mHandler.sendEmptyMessage(1);
            }

        }
    }

    public MapRenderer getMapRender() {
        return mRenderer;
    }

    /**
     * 初始化放大图绘制使用的view
     *
     * @param view
     */
    public void setExpandView(ImageView view) {
        mExpandView = view;
    }

    /**
     * 开始模拟导航
     */
    public void startSimulation() {
        backupMapStateBeforeSimulation();
    }

    public void drawHighwayGuide() {

    }

    /**
     * 绘制放大图
     */
    public void drawExpandView() {

    }

    /**
     * 删除箭头
     */
    public void delArrow() {
        if (mArrowOverlay != null) {
            mRenderer.removeOverlay(mArrowOverlay);
            mArrowOverlay = null;
        }
    }

    /**
     * 设置路线是否开启Tmc模式
     */
    public void setRouteTmc(boolean isTmc) {
        if (mRouteCollectionOverlays[0] != null) {
            if (isTmc) {
                mRouteCollectionOverlays[0].enableTmcColors(isTmc);
            } else {
                mRouteCollectionOverlays[0].enableTmcColors(isTmc);
                mRouteCollectionOverlays[0].setColor(mRouteOverlayColors[0]);
            }
        }
    }

    /**
     * 将路线显示在地图上
     *
     * @param index
     */
    public void drawRouteToMap(int index) {
        if (mRouteCollectionOverlays != null && index < mRouteOverlayNumber) {
            mRouteCollectionOverlays[index].setHidden(false);
        }
    }

    /**
     * 点击气泡
     */
    @Override
    public void onAnnotationClicked(Annotation annot, int area) {
        super.onAnnotationClicked(annot, area);
        annot.showCallout(true);
        mClickPoint = new Point(mRenderer.getWorldCenter());

        if (!isAvaliableDistance()) {
            showMessage("您不在可操作范围，请重新定位当前位置");
            annot.showCallout(false);
            return;
        }


        switch (area) {
            case Annotation.Area.leftButton:
                annot.showCallout(false);
                break;
            case Annotation.Area.rightButton:
                annot.showCallout(false);
                break;
            case Annotation.Area.middleButton:
                // 气泡中间
                annot.showCallout(false);
                if (annot.getTag() == -1) {
                    if (null == myCusInfo) {//地图标记
                        CustomerInfo cInfo = new CustomerInfo();
                        cInfo.setGpsLat(String.valueOf((mClickPoint.y) / 1E5));
                        cInfo.setGpsLng(String.valueOf((mClickPoint.x) / 1E5));
                        cInfo.setAddress(annot.getTitle());
                        String[] content = {"个人客户", "对公客户"};
                        showSelectDialog(content, "请选择新建客户类型", cInfo);
                    } else {
                        if (MapType == 2 || MapType == 3)//单个客户标记，日程地图，客户地图
                        {
                            JSONObject obj = new JSONObject();
                            try {
                                obj.put("customerId", myCusInfo.getCustomerId());
                                obj.put("gpsLat", String.valueOf((mClickPoint.y) / 1E5) + "");
                                obj.put("gpsLng", String.valueOf((mClickPoint.x) / 1E5) + "");
                                obj.put("customerType", myCusInfo.getCustomerType());
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            new SetCustomerLatLngTask().execute(obj.toString(), getAccount());//客户定位
                            myCusInfo.setAddress(annot.getTitle());
                        }
                    }

                } else if (annot.getTag() == 2)//客户管理定位点击气泡跳转到客户详情
                {
                    Intent intent = new Intent(mContext, CDetailActivity.class);
                    Bundle bundle = new Bundle();
                    bundle.putSerializable("customer", myCusInfo);
                    intent.putExtras(bundle);
                    mContext.startActivity(intent);
                } else if (annot.getTag() == 3) {
                    //新增客户
                    mClickPoint = new Point(mRenderer.getWorldCenter());
                    CustomerInfo cInfo = new CustomerInfo();
                    cInfo.setGpsLat(String.valueOf((mClickPoint.y) / 1E5));
                    cInfo.setGpsLng(String.valueOf((mClickPoint.x) / 1E5));
                    if (!TextUtils.isEmpty(annot.getTitle())) {
                        String address = annot.getTitle();
                        if (address.contains("当前地址：")) {
                            address = address.replace("当前地址：", "");
                            cInfo.setAddress(address);
                        } else {
                            cInfo.setAddress(annot.getTitle());
                        }
                    }

                    String[] content = {"个人客户", "对公客户"};
                    showSelectDialog(content, "请选择新建客户类型", cInfo);
                }
                break;
            case Annotation.Area.none:
            case Annotation.Area.icon:
                annot.showCallout(true);
            default:
                break;
        }
    }


    //判断当前位置是否能进行定位,小于最小距离则可以进行新建
    private boolean isAvaliableDistance() {
        if (currentDistance() < Constance.AVALIABLEDISTANCE && currentDistance() != -1) {
            return true;
        }
        return false;
    }

    //计算当前位置和标注点坐标点之间的距离
    private double currentDistance() {
        double currentLat = getCurrentLat();
        double currentLng = getCurrentLng();
        if (currentLat == -1 || currentLng == -1) {
            return -1;
        }
        return DistanceUitles.calculateDistance(currentLng, currentLat, (mClickPoint.y) / 1E5, (mClickPoint.x) / 1E5);
    }

    //得到当前的经度
    private double getCurrentLng() {
        String pointLng = mContext.getSharedPreferences(mContext.getPackageName(), mContext.MODE_WORLD_READABLE).getString(PREFERENCE_LNG_KEY, "-1");
        if (TextUtils.isEmpty(pointLng))
            return -1;
        return Double.parseDouble(pointLng);
    }

    //得到当前的纬度
    private double getCurrentLat() {
        String pointLat = mContext.getSharedPreferences(mContext.getPackageName(), mContext.MODE_WORLD_READABLE).getString(PREFERENCE_LAT_KEY, "-1");
        if (TextUtils.isEmpty(pointLat))
            return -1;
        return Double.parseDouble(pointLat);
    }

    /**
     * 选择icon
     */
    @Override
    public void onAnnotationSelected(Annotation arg0) {
        super.onAnnotationSelected(arg0);
        arg0.showCallout(true);
    }

    /**
     * 取消icon
     */
    @Override
    public void onAnnotationDeselected(Annotation annot) {
        super.onAnnotationDeselected(annot);
        annot.showCallout(false);
    }

    private void showAnnotation(Annotation annot) {
        mPoiAnnotation.showCallout(true);
        mPositionAnnotation.showCallout(true);
        if (annot != null) {
            annot.showCallout(true);
            // mIsAnnotationDisplaying = true;
        }
    }

    private void hideAnnotation(Annotation annot) {
        mPoiAnnotation.showCallout(false);
        mPositionAnnotation.showCallout(false);
        if (annot != null) {
            annot.showCallout(false);
            // mIsAnnotationDisplaying = true;
        }
    }

    public CustomAnnotation getmCustomAnnotation() {
        return mCustomAnnotation;
    }

    /**
     * 点击poi
     */
    @Override
    public void onPoiSelected(String name, Point point) {
        super.onPoiSelected(name, point);
//		// TODO: 替換mRenderer方法
//		mClickPoint.set(point.x,point.y);
//		mPoiAnnotation.setTitle(name);
//		mPoiAnnotation.setPosition(mClickPoint);
//		showAnnotation(mPoiAnnotation);
//		mRenderer.beginAnimations();
//		mRenderer.setWorldCenter(mClickPoint);
//		mRenderer.commitAnimations(500, MapRenderer.Animation.linear);
    }

    @Override
    public void onPoiDeselected(String name, Point point) {
        super.onPoiDeselected(name, point);
        mPoiAnnotation.showCallout(false);
    }

    /**
     * 备份地图和小车的状态以便模拟导航之后可以恢复
     */
    private void backupMapStateBeforeSimulation() {
        mMapState = mRenderer.getMapState();
        mCarPosition = mCarOverlay.getPosition();
        mCarOriented = mCarOverlay.getHeading();
    }

    /**
     * 模拟导航结束之后恢复地图和小车之前的状态
     */
    private void resetMapStateAfterSimulation() {
        mRenderer.setMapState(mMapState);
        mCarOverlay.setPosition(mCarPosition);
        mCarOverlay.setHeading((int) mCarOriented);
    }


    /**
     * 控制是否锁车
     *
     * @param lock 是否锁车，如果为true则锁车，否则不锁车
     */
    public void lockCar(boolean lock) {
        if (lock != mIsLockedCar) {
            mIsLockedCar = lock;
            if (mIsLockedCar) {
                mRenderer.setWorldCenter(mCarOverlay.getPosition());
                mRenderer.setHeading(360.0f - mCarOverlay.getHeading());
                mRenderer.setViewShift(0.3f);
            }
        }
    }

    /**
     * 判断是否为锁车状态
     *
     * @return 如果锁车则返回true，否则返回false
     */
    public boolean carIsLocked() {
        return mIsLockedCar;
    }

    /**
     * 设置车的位置，用于在模拟导航时更新车的位置使用
     *
     * @param point 车所在位置
     */
    public void setCarPosition(Point point) {
        if (mCarOverlay != null) {
            mCarOverlay.setPosition(point);
        }
        if (mIsLockedCar && mRenderer != null) {
            mRenderer.setWorldCenter(point);
        }
    }

    /**
     * 获取车当前的位置
     *
     * @return 车当前的位置坐标
     */
    public Point getCarPosition() {
        return mCarOverlay.getPosition();
    }

    /**
     * 设置当前车的角度，用于导航时更新车的角度
     *
     * @param ori 车的角度
     */
    public void setCarOriented(float ori) {
        mCarOverlay.setHeading((int) ori);
        if (mIsLockedCar) {
            mRenderer.setHeading(360.0f - ori);
        }
    }

    /**
     * 在地图指定位置显示一个POI的信息
     *
     * @param point POI所在位置
     * @param name  POI名称
     */
    public void showPoiAnnotation(Point point, String name) {
        mRenderer.setWorldCenter(point);
        mClickPoint.set(point.x, point.y);
        mPoiAnnotation.setTitle(name);
        mPoiAnnotation.setPosition(point);
        showAnnotation(mPoiAnnotation);
    }

    /**
     * 获取当前车的角度
     *
     * @return 车的较粗
     */
    public float getCarOriented() {
        return (float) mCarOverlay.getHeading();
    }

    /**
     * 将指定的路线隐藏
     *
     * @param index
     */
    public void removeRouteOverlay(int index) {
        if (mRouteCollectionOverlays[index] != null) {
            mRenderer.removeOverlay(mRouteCollectionOverlays[index]);
            mRouteCollectionOverlays[index] = null;
        }
    }

    /**
     * 删除所有路线
     *
     * @param removeAll
     */
    private void removeRouteOverlay(boolean removeAll) {
        for (int i = 0; i < mRouteOverlayNumber; i++) {
            if (removeAll) {
                removeRouteOverlay(i);
            }
        }
        if (removeAll) {
            mRouteOverlayNumber = 0;
        } else {
            mRouteOverlayNumber = 1;
        }
        if (mArrowOverlay != null) {
            mRenderer.removeOverlay(mArrowOverlay);
            mArrowOverlay = null;
        }
    }

    /**
     * 地图放大操作
     *
     * @param zoomIn  放大按钮
     * @param zoomOut 缩小按钮
     */
    public void mapZoomIn(ImageView zoomIn, ImageView zoomOut) {
        float zoomLevel = mRenderer.getZoomLevel();
        if (mZoomLevelRange == null) {
            mZoomLevelRange = mRenderer.getZoomLevelRange();
        }
        zoomLevel = zoomLevel + ZOOM_STEP;
        if (zoomLevel >= mZoomLevelRange.getY()) {
            zoomLevel = mZoomLevelRange.getY();
            zoomIn.setEnabled(false);
        }
        zoomOut.setEnabled(true);
        mRenderer.beginAnimations();
        mRenderer.setZoomLevel(zoomLevel);
        mRenderer.commitAnimations(300, MapRenderer.Animation.linear);
    }

    /**
     * 地图缩小操作
     *
     * @param zoomIn  放大按钮
     * @param zoomOut 缩小按钮
     */
    public void mapZoomOut(ImageView zoomIn, ImageView zoomOut) {
        float zoomLevel = mRenderer.getZoomLevel();
        if (mZoomLevelRange == null) {
            mZoomLevelRange = mRenderer.getZoomLevelRange();
        }
        zoomLevel = zoomLevel - ZOOM_STEP;
        if (zoomLevel <= mZoomLevelRange.getX()) {
            zoomLevel = mZoomLevelRange.getX();
            zoomOut.setEnabled(false);
        }
        zoomIn.setEnabled(true);
        mRenderer.beginAnimations();
        mRenderer.setZoomLevel(zoomLevel);
        mRenderer.commitAnimations(300, MapRenderer.Animation.linear);
    }

    /**
     * 检查网络wifi 2G 3G网络
     *
     * @return TODO
     */
    public boolean isOpenNet() {
        ConnectivityManager connManager = (ConnectivityManager) mContext
                .getApplicationContext().getSystemService(
                        Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connManager.getActiveNetworkInfo();
        if (networkInfo != null) {
            return networkInfo.isAvailable();
        }
        return false;
    }

    /**
     * 检查gps是否开启
     *
     * @return
     */
    @SuppressWarnings("deprecation")
    public boolean isOpenGps() {
        return Settings.Secure.isLocationProviderEnabled(mContext.getContentResolver(), LocationManager.GPS_PROVIDER);
    }


    @Override
    public void onCameraChanged(int changeTye) {
        super.onCameraChanged(changeTye);
        MapRenderer render = getMapRenderer();
        if (render != null) {
            if (changeTye == MapRenderer.CameraSetting.zoomLevel + MapRenderer.CameraSetting.scale) {
                //地图缩放
                zoomChange();
            }
        }
    }

    // ////////////////////////////////////////////////
    // OnTouchListener
    // ////////////////////////////////////////////////

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (mGestureDetector.onTouchEvent(event)) {
            return true;
        }
        int actionAndIndex = event.getAction();
        int action = actionAndIndex & MotionEvent.ACTION_MASK;
        switch (action) {
            case MotionEvent.ACTION_MOVE:
                //手动无级缩放时 要注意改变放大缩小是否可用
                zoomChange();
        }
        return super.onTouch(v, event);
    }

    /**
     * 缩放级别改变
     */
    public void zoomChange() {
        if (mRenderer == null)
            return;
        float zoomLevel = mRenderer.getZoomLevel();
        Message msg = new Message();
        msg.what = 100;
        Bundle b = msg.getData();
        // 默认都可用
        b.putBoolean("zoomIn", true);
        b.putBoolean("zoomOut", true);
        if (mZoomLevelRange == null) {
            mZoomLevelRange = mRenderer.getZoomLevelRange();
        }
        //判断放大缩小是否可用
        if (zoomLevel <= mZoomLevelRange.getX()) {
            b.putBoolean("zoomIn", false);
        }
        if (zoomLevel >= mZoomLevelRange.getY()) {
            b.putBoolean("zoomOut", false);
        }
        // 发送消息
        if (mHandler != null) {
            mHandler.sendMessage(msg);
        }
    }

    @SuppressWarnings("deprecation")
    private GestureDetector mGestureDetector = new GestureDetector(
            new OnGestureListener() {

                @Override
                public boolean onSingleTapUp(MotionEvent arg0) {
                    return false;
                }

                @Override
                public void onShowPress(MotionEvent arg0) {

                }

                @Override
                public boolean onScroll(MotionEvent arg0, MotionEvent arg1,
                                        float arg2, float arg3) {
                    return false;
                }

                @Override
                public void onLongPress(MotionEvent event) {
                    int pointerCount = event.getPointerCount();
                    if (pointerCount == 1) {
                        Point point = mRenderer.screen2World(new PointF(event
                                .getX(), event.getY()));
                        mClickPoint.set(point.x, point.y);
                        mReverseGeocoder.start(mClickPoint);
                        mPositionAnnotation.setPosition(mClickPoint);
                    }
                }

                @Override
                public boolean onFling(MotionEvent arg0, MotionEvent arg1,
                                       float arg2, float arg3) {
                    return false;
                }

                @Override
                public boolean onDown(MotionEvent arg0) {
                    return false;
                }
            });

    @Override
    public void onReverseGeoRequest(ReverseGeocoder geocoder, int event, int err, java.lang.Object userData) {
        switch (event) {
            case ReverseGeocoder.Event.none:
                break;
            case ReverseGeocoder.Event.failed:
                String msg = null;
                switch (err) {
                    case ReverseGeocoder.Error.netError:
                        msg = "网络错误";
                        break;
                    case ReverseGeocoder.Error.noData:
                        msg = "没有本地离线数据";
                        break;
                    case ReverseGeocoder.Error.none:
                        break;
                    case ReverseGeocoder.Error.noResult:
                        msg = "无搜索结果";
                        break;
                    case ReverseGeocoder.Error.noPermission:
                        msg = "没有权限";
                        break;
                }
                hideAnnotation(null);
                if (msg != null) {
                    showMessage(msg);
                }
                break;
            case ReverseGeocoder.Event.started:
                break;
            case ReverseGeocoder.Event.succeeded:
                ReverseGeocoderDetail result = mReverseGeocoder.getResult();
                mPositionAnnotation.setTitle(result.poiCity + result.poiArea + result.poiName);
                //showAnnotation(mPositionAnnotation);
                showAnnotation(null);
                MapRenderer mr = mRenderer;
                mr.beginAnimations();
                mr.setWorldCenter(mClickPoint);
                mr.commitAnimations(500, MapRenderer.Animation.linear);
                break;
        }
    }

    private Customer setCustomer(CustomerInfo info) {
        Customer customer = new Customer();
        if (info != null) {
            customer.setCustomerId(info.getCustomerId());
            customer.setCustomerName(info.getCustomerName());
            customer.setSex(info.getSex());
            customer.setIdCard(info.getIdCard());
            customer.setRemark(info.getRemark());
            customer.setAddress(info.getAddress());
            customer.setMobilePhone1(info.getMobilePhone1());
            customer.setMobilePhone2(info.getMobilePhone2());
            customer.setPhone(info.getPhone());
            customer.setDefaultPhoneType(info.getDefaultPhoneType());
            customer.setIsLimited(info.getIsLimited());
        }
        return customer;
    }

    /**
     * 异步地图标注客户位置
     */
    class SetCustomerLatLngTask extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... params) {
            String data = null;
            try {
                new MapWebService(mContext);
                data = MapWebService.addCustomerLngLat(mContext, params[1], params[0]);
            } catch (Exception e) {

            }
            return data;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            if ("1".equals(s.toString())) {
                Gson gson = new GsonBuilder().create();
                String jsonString = gson.toJson(setCustomer(myCusInfo));
                new SaveCustomerTask().execute(jsonString, getAccount());//更新客户地址
            }
        }
    }

    /**
     * 异步更新客户地址
     */
    class SaveCustomerTask extends AsyncTask<String, Void, CustomResult> {

        @Override
        protected CustomResult doInBackground(String... params) {
            CustomResult customResult = null;
            try {
                new CustomerWebService(mContext);
                String data = CustomerWebService.saveCustomer(mContext, params[1], params[0]);
                Gson gson = new GsonBuilder().create();
                customResult = gson.fromJson(data, new TypeToken<CustomResult>() {
                }.getType());
                return customResult;
            } catch (Exception e) {

            }
            return null;
        }

        @Override
        protected void onPostExecute(CustomResult s) {
            super.onPostExecute(s);
            if ("false".equals(s.getMessageCode())) {
                showMessage("更新客户位置失败！");
            } else {
                showMessage("更新客户位置成功！");
                if (null != mCustomAnnotation) {
                    mRenderer.removeAnnotation(mCustomAnnotation);
                }
                //初始化Overlay和Annotation 添加车标
                if (MapType == 2) {
                    if (!TextUtils.isEmpty(myCusInfo.getLoanStatus())) {
                        addPoint(R.drawable.map_user_icon, mClickPoint, myCusInfo, MapType);
                    } else {
                        addPoint(R.drawable.map_cus_icon_up, mClickPoint, myCusInfo, MapType);
                    }
                } else if (MapType == 3) {

                    addPoint(R.drawable.map_cus_icon_down, mClickPoint, myCusInfo, MapType);
                }

            }
        }
    }

    public String getAccount() {
        String username = mContext.getSharedPreferences(mContext.getPackageName(), mContext.MODE_WORLD_READABLE).getString("ACCOUNT", "");
        return username;
    }

    public void showMessage(String msg) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.toast_message,
                null);
        TextView txt = (TextView) view.findViewById(R.id.toast_message);
        Toast toast = new Toast(mContext);
        toast.setView(view);
        txt.setText(msg);
        toast.setGravity(Gravity.CENTER, 0, 200);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.show();
    }

    /**
     * 根据客户的坐标点进行标记
     */
    public void addPoint(int vid, Point point, CustomerInfo cInfo, int tag) {
        Vector2DF pivot = new Vector2DF(0.5f, 0.82f);
        mCustomAnnotation = new CustomAnnotation(2, point,
                // 此参数为气泡id 不能重复
                cInfo.getCustomerId(), pivot, BitmapFactory.decodeResource(
                getResources(), vid));
        mCustomAnnotation.setClickable(true);
        mCustomAnnotation.setSelected(true);
        String cusName = cInfo.getCustomerName();
        if (TextUtils.isEmpty(cusName)) {
            cusName = "无";
        }
        String cusAddress = cInfo.getAddress();
        if (TextUtils.isEmpty(cusAddress)) {
            cusAddress = "无";
        }
        mCustomAnnotation.setTitle("客户姓名：" + cusName + "\n"
                        + "客户地址：" + cusAddress
        );
        mCustomAnnotation.setTag(tag);
        mRenderer.addAnnotation(mCustomAnnotation);
        mCustomAnnotation.showCallout(true);

    }

    /**
     * 定位
     */
    public void getLocation() {

        String lat = mContext.getSharedPreferences(mContext.getPackageName(), mContext.MODE_WORLD_READABLE).getString(PREFERENCE_LAT_KEY, "");
        String lng = mContext.getSharedPreferences(mContext.getPackageName(), mContext.MODE_WORLD_READABLE).getString(PREFERENCE_LNG_KEY, "");
        /*showMessage("正在定位,请稍候...");*/
        if (!TextUtils.isEmpty(lat) && !TextUtils.isEmpty(lng)) {
            Point pointLocation = new Point();
            int x = (int) (Double.valueOf(lng) * 1E5);
            int y = (int) (Double.valueOf(lat) * 1E5);
            pointLocation.set(x, y);
            //初始化Overlay和Annotation 添加定位标志
            if (null != mCarIcon)//移除上次定位图标
            {
                mRenderer.removeOverlay(mCarIcon);
            }
            mCarIcon = new IconOverlay("res/1001@h.png", true);
            mCarIcon.setPosition(pointLocation);
            //mCarIcon.setOrientAngle(0.0f);
            mRenderer.addOverlay(mCarIcon);
            mRenderer.setWorldCenter(pointLocation);
        }
    }


    public boolean getLocationState() {
        boolean result = false;
        String lat = mContext.getSharedPreferences(mContext.getPackageName(), mContext.MODE_WORLD_READABLE).getString(PREFERENCE_LAT_KEY, "");
        String lng = mContext.getSharedPreferences(mContext.getPackageName(), mContext.MODE_WORLD_READABLE).getString(PREFERENCE_LNG_KEY, "");
        /*showMessage("正在定位,请稍候...");*/
        if (!TextUtils.isEmpty(lat) && !TextUtils.isEmpty(lng)) {
            Point pointLocation = new Point();
            int x = (int) (Double.valueOf(lng) * 1E5);
            int y = (int) (Double.valueOf(lat) * 1E5);
            pointLocation.set(x, y);
            //初始化Overlay和Annotation 添加定位标志
            if (null != mCarIcon)//移除上次定位图标
            {
                mRenderer.removeOverlay(mCarIcon);
            }
            mCarIcon = new IconOverlay("res/1001@h.png", true);
            mCarIcon.setPosition(pointLocation);
            //mCarIcon.setOrientAngle(0.0f);
            mRenderer.addOverlay(mCarIcon);
            mRenderer.setWorldCenter(pointLocation);
            result = true;
        } else {
            showMessage("暂未获取到定位信息，重新定位，请稍后重试");
        }
        return result;
    }

    /**
     * 选择对话框
     */
    private void showSelectDialog(final String[] strings, final String title, final CustomerInfo cInfo) {
        final AlertDialog ad = new AlertDialog.Builder(mContext).create();
        final View view = View.inflate(mContext,
                R.layout.product_addintent_product, null);
        TextView txt = (TextView) view.findViewById(R.id.dialog_list_title);
        ListView mLV = (ListView) view.findViewById(R.id.dialog_list);
        txt.setText(title);
        StringAdapter adapter = new StringAdapter(mContext,
                strings);
        mLV.setAdapter(adapter);

        ad.setView(view, 0, 0, 0, 0);
        ad.show();

        mLV.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int pos,
                                    long arg3) {
                Intent intent = new Intent(mContext, AddCusActivity.class);
                Bundle bundle = new Bundle();
                if (strings[pos].equals("对公客户")) {
                    bundle.putInt("cusType", 1);
                } else if (strings[pos].equals("个人客户")) {
                    bundle.putInt("cusType", 0);
                }
                bundle.putSerializable("customer", cInfo);
                bundle.putBoolean("isAdd", true);
                bundle.putInt("status", 3);
                intent.putExtras(bundle);
                ((McpMapActivity) mContext).startActivityForResult(intent, Constance.ADD_ADDRESS_CUSTOMER);
                ad.dismiss();
            }
        });
    }
}

