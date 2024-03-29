package jp.pinetail.android.wifi.switcher.overlay;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jp.pinetail.android.wifi.switcher.GeoHex;
import jp.pinetail.android.wifi.switcher.GeoHex.Loc;
import jp.pinetail.android.wifi.switcher.GeoHex.Zone;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Point;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

/**
 * GeoHex を描画する Overlay
 *
 * MapView に追加すると GeoHex を画面いっぱいに描画します。
 * MapView の ZoomLevel に応じて、GeoHex のレベルも変化します。
 * タップで選択/選択解除ができます。
 * 選択された GeoHex(のコード)群は、getSelectedGeoHexCodes() で得られます。
 *
 */
public class GeoHexOverlay extends Overlay implements OnGestureListener {

	public interface OnTapHexListener {
		void onTap(GeoHexOverlay sender, String hexCode);
	}

	// fields -----------------------------------------------------------------
	final private int MIN_ZOOMLEVEL = 2;

	private GestureDetector gestureDetector = null;

	/** GeoHex の描画スタイル */
	private Paint hexPaint = new Paint();

	/** 選択した GeoHex の描画スタイル */
	private Paint selectionPaint = new Paint();

	private Paint imagePaint = new Paint();
	private Bitmap bmpRinger = null;

	/** 選択した GeoHex の Code 群 */
	private Set<String> selectedGeoHexCodes = new HashSet<String>();

	/** 選択時の GeoHex のレベル(今は GoogleMap の ZoomLV と連動) */
	private int geoHexLevel;

	private MapView lastTouchMapView = null;

	private OnTapHexListener onTapHexListener = null;

	public OnTapHexListener getOnTapHexListener() {
		return onTapHexListener;
	}

	public void setOnTapHexListener(OnTapHexListener onTapHexListener) {
		this.onTapHexListener = onTapHexListener;
	}

	// ctor -------------------------------------------------------------------
	public GeoHexOverlay() {
		hexPaint.setStyle(Style.STROKE);
		hexPaint.setColor(Color.LTGRAY);
		hexPaint.setStrokeWidth(1f);
		hexPaint.setAntiAlias(true);

		selectionPaint.setStyle(Style.FILL);
		selectionPaint.setColor(Color.argb(64, 128, 0, 0));
		selectionPaint.setStrokeWidth(2f);
		selectionPaint.setAntiAlias(true);

		gestureDetector = new GestureDetector(this);
	}

	// setter/getter ----------------------------------------------------------
	/** 選択された GeoHex のコード群 を設定します。 */
	public void setSelectedGeoHexCodes(Set<String> selectedGeoHexCodes) {
		this.selectedGeoHexCodes = selectedGeoHexCodes;
	}

	/** 選択された GeoHex のコード群 を取得します。 */
	public Set<String> getSelectedGeoHexCodes() {
		return selectedGeoHexCodes;
	}

	// overrides --------------------------------------------------------------
	/** GeoHex を画面いっぱいに描画 & 選択されている GeoHex も描画 */
	@Override
	public void draw(Canvas canvas, MapView mapView, boolean shadow) {
		super.draw(canvas, mapView, shadow);
		if (shadow) { return; }

		// 世界地図だと 0 度またぎの対応が面倒なので、Level3 くらいまでの対応にする
		if (mapView.getZoomLevel() <= MIN_ZOOMLEVEL) { return; }

		// GeoHex のレベルは GoogleMap と連動
		geoHexLevel = mapView.getZoomLevel() - 1;

		GeoPoint geoCenter = mapView.getMapCenter();
		Projection proj = mapView.getProjection();

		// 中心位置の GeoHex を取得
		Zone centerZone = GeoHex.getZoneByLocation(geoCenter.getLatitudeE6() / 1E6, geoCenter.getLongitudeE6() / 1E6, geoHexLevel);
		// 中心からどのくらい膨らましたら画面いっぱいになるかを計算
		int inflate = (int) Math.ceil(getLatitudeSpanInMetre(mapView) / (centerZone.getHexSize() * 2) / 2d);

		// 画面いっぱい分の GeoHex群を得て描画
		Point[] points;
		List<Zone> zones = centerZone.inflate(inflate);
		for (Zone zone : zones) {
			points = getGeoHexZonePoints(zone, proj);
			drawPolyline(canvas, points, hexPaint);
		}

		// 選択したものを描画
		// TODO: 直接 values 使えばいいじゃん
		for (String code : getSelectedGeoHexCodes()) {
			Zone z = GeoHex.getZoneByCode(code);
			points = getGeoHexZonePoints(z, proj);
			drawPolyline(canvas, points, selectionPaint);

			if (bmpRinger != null) {
				Point p = new Point();
				proj.toPixels(new GeoPoint((int)(z.lat * 1E6), (int)(z.lon * 1E6)), p);
				canvas.drawBitmap(bmpRinger,
						p.x - (bmpRinger.getWidth() / 2),
						p.y - (bmpRinger.getHeight() / 2), imagePaint);
			}
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent e, MapView mapView) {
		Log.d(this.getClass().getSimpleName(), "onTouchEvent() Action:" + String.valueOf(e.getAction()));
		Log.d(this.getClass().getSimpleName(), "onTouchEvent() DownTime:" + String.valueOf(e.getDownTime()));
		Log.d(this.getClass().getSimpleName(), "onTouchEvent() EventTime:" + String.valueOf(e.getEventTime()));

		lastTouchMapView = mapView;
		gestureDetector.onTouchEvent(e);

		return super.onTouchEvent(e, mapView);
	}


	// public methods ---------------------------------------------------------

	// private methods --------------------------------------------------------
	/** 指定した GeoHex を構成する画面座標値を得ます。(最後を閉じるので7点の座標値) */
	public Point[] getGeoHexZonePoints(Zone zone, Projection proj) {
		Loc[] locs = zone.getHexCoords();

		Point[] points = new Point[locs.length + 1];
		for (int i = 0; i < locs.length; i++) {
			Loc loc = locs[i];
			Point p = new Point();
			proj.toPixels(new GeoPoint((int)(loc.lat * 1E6), (int)(loc.lon * 1E6)), p);
			points[i] = new Point();
			points[i].set(p.x, p.y);

		}

		// 閉じる
		points[points.length-1] = new Point();
		points[points.length-1].set(points[0].x, points[0].y);
		return points;
	}

	/** Point[] をポリライン/ポリゴン として描画します。 */
	private void drawPolyline(Canvas canvas, Point[] points, Paint paint) {

		Path path = null;
		for (int i = 0; i < points.length; i++) {
			Point p1 = points[i % points.length];

			if (path == null) {
				path = new Path();
				path.moveTo(p1.x, p1.y);
			} else {
				path.lineTo(p1.x, p1.y);
			}

		}
		canvas.drawPath(path, paint);
	}

	/** MapView の縦方向のワールドサイズをメートル値で取得します。 */
	private double getLatitudeSpanInMetre(MapView mapView) {

		float heightInMetre = (float)(mapView.getLatitudeSpan() / 1E6) * 111136f;
		return heightInMetre;

	}

	@Override
	public boolean onDown(MotionEvent e) { return false; }

	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
			float velocityY) { return false; }

	@Override
	public void onLongPress(MotionEvent e) { }

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
			float distanceY) { return false; }

	@Override
	public void onShowPress(MotionEvent e) { }

	@Override
	public boolean onSingleTapUp(MotionEvent e) {
		Log.d(this.getClass().getSimpleName(), "onSingleTapUp");

		if (lastTouchMapView == null) {
			return false;
		}

		// 世界地図だと 0 度またぎの対応が面倒なので、Level3 くらいまでの対応にする
		if (lastTouchMapView.getZoomLevel() <= MIN_ZOOMLEVEL) { return false; }

		GeoPoint p = lastTouchMapView.getProjection().fromPixels((int)e.getX(), (int)e.getY());

		// タップ位置の GeoHex を得る
		Zone zone = GeoHex.getZoneByLocation(p.getLatitudeE6() / 1E6, p.getLongitudeE6() / 1E6, geoHexLevel);
		if (zone == null) { return false; }

		if (onTapHexListener != null) {
			onTapHexListener.onTap(this, zone.code);
		}

		return false;
	}

	public void setBitmap(Bitmap bitmap) {
		bmpRinger = bitmap;
	}
}
