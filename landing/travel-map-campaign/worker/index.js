export default {
  async fetch(request, env) {
    const response = await env.ASSETS.fetch(request);
    const acceptsHtml = request.headers.get("accept")?.includes("text/html");
    const indexUrl = new URL(request.url);
    const isApiRequest = indexUrl.pathname === "/api" || indexUrl.pathname.startsWith("/api/");

    if (response.status !== 404 || isApiRequest || !acceptsHtml || !["GET", "HEAD"].includes(request.method)) {
      return response;
    }

    indexUrl.pathname = "/index.html";
    indexUrl.search = "";
    return env.ASSETS.fetch(new Request(indexUrl, request));
  },
};
