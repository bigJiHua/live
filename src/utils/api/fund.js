import request from "@/utils/request";

export function getFundList() {
  return request.get("/fund/list");
}

export function getFund(id) {
  return request.get(`/fund/${id}`);
}

export function createFund(data) {
  return request.post("/fund", data);
}

export function updateFund(id, data) {
  return request.put(`/fund/${id}`, data);
}

export function deleteFund(id) {
  return request.delete(`/fund/${id}`);
}

export function getFundHistory(fundId, options = 180) {
  const params = typeof options === "object" && options !== null ? { ...options } : { limit: options };
  Object.keys(params).forEach((key) => {
    if (params[key] === undefined || params[key] === null || params[key] === "") {
      delete params[key];
    }
  });
  return request.get(`/fund/${fundId}/history`, { params });
}

export function getAllFundHistory(limit = 180) {
  return request.get("/fund/history/all", { params: { limit } });
}

export function addFundHistory(fundId, data) {
  return request.post(`/fund/${fundId}/history`, data);
}

export function updateFundHistory(id, data) {
  return request.put(`/fund/history/${id}`, data);
}

export function deleteFundHistory(id) {
  return request.delete(`/fund/history/${id}`);
}
