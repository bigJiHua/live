/**
 * 地理定位权限（不重试、不循环）
 *
 * 注意：Chrome 对重复弹窗有自动拦截机制
 * https://www.chromestatus.com/feature/6443143280984064
 */

export async function getGeoPermissionState() {
  if (!navigator.permissions) return "unsupported";
  try {
    const r = await navigator.permissions.query({ name: "geolocation" });
    return r.state;
  } catch { return "unsupported"; }
}

/**
 * 请求一次 GPS，只调一次，不重试。
 * enableHighAccuracy=false 对移动端友好，避免 timeout。
 */
export function requestGpsOnce() {
  return new Promise((resolve) => {
    if (!navigator.geolocation) {
      return resolve({ granted: false, lat: 0, lng: 0, reason: "unsupported" });
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve({ granted: true, lat: pos.coords.latitude, lng: pos.coords.longitude }),
      (err) => resolve({
        granted: false, lat: 0, lng: 0,
        reason: err.code === 1 ? "denied" : err.code === 2 ? "unavailable" : err.code === 3 ? "timeout" : "unknown",
      }),
      { enableHighAccuracy: false, timeout: 8000, maximumAge: 30000 }
    );
  });
}

/**
 * 安全请求 GPS：先查权限，denied 直接返回不调 GPS
 */
export async function safeRequestGps() {
  const state = await getGeoPermissionState();
  if (state === "denied") return { granted: false, lat: 0, lng: 0, reason: "denied" };
  return requestGpsOnce();
}
