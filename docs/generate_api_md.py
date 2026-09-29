"""Regenerates docs/API.md from docs/openapi.json.

Refresh the spec first (app running on port 8090):
    curl -s localhost:8090/v3/api-docs | python3 -m json.tool > docs/openapi.json
Then:
    python3 docs/generate_api_md.py
"""
import json
from pathlib import Path

DOCS = Path(__file__).parent
ORDER = ["Auth", "Browse - Cities & Theaters", "Browse - Movies", "Browse - Shows", "Bookings", "Payments",
         "Cancellations & Refunds", "Booking History", "Notifications",
         "Admin - Cities", "Admin - Theaters", "Admin - Screens & Seat Layouts", "Admin - Movies", "Admin - Shows",
         "Admin - Pricing Rules", "Admin - Discount Codes", "Admin - Refund Policies"]
METHOD_ORDER = {"GET": 0, "POST": 1, "PUT": 2, "DELETE": 3}

spec = json.loads((DOCS / "openapi.json").read_text())
descriptions = {t["name"]: t.get("description", "") for t in spec.get("tags", [])}
ops = {}
for path, item in spec["paths"].items():
    for method, op in item.items():
        ops.setdefault(op["tags"][0], []).append((path, method.upper(), op))
unknown = set(ops) - set(ORDER)
if unknown:
    raise SystemExit(f"Add these tags to ORDER: {sorted(unknown)}")

out = ["# API reference", "",
       "Generated from the OpenAPI spec ([openapi.json](openapi.json)) by `docs/generate_api_md.py`. The live,",
       "interactive version is Swagger UI at `/swagger-ui.html` once the app runs.", "",
       "**Auth:** `public` = no token; `token` = `Authorization: Bearer <accessToken>` from `POST /api/auth/login`.",
       "Routes under `/api/admin/**` also need the ADMIN role.", "",
       f"**{sum(len(v) for v in ops.values())} operations** in {len(ops)} areas. Errors use the standard `ApiError` "
       "shape; see the [error catalogue](DESIGN.md#error-catalogue).", ""]
for tag in [t for t in ORDER if t in ops]:
    out.append(f"## {tag}")
    if descriptions.get(tag):
        out.append(f"_{descriptions[tag]}_")
    out += ["", "| Method | Path | Auth | Description | Responses |", "|---|---|---|---|---|"]
    for path, method, op in sorted(ops[tag], key=lambda x: (x[0], METHOD_ORDER[x[1]])):
        params = [p["name"] + ("*" if p.get("required") else "") for p in op.get("parameters", [])
                  if p.get("in") in ("query", "header")]
        desc = op.get("summary", "").replace("|", "\\|")
        if params:
            desc += f" <br>Params: `{'`, `'.join(params)}`"
        codes = ", ".join(sorted(op.get("responses", {})))
        auth = "token" if op.get("security") else "public"
        out.append(f"| `{method}` | `{path}` | {auth} | {desc} | {codes} |")
    out.append("")
out.append("`*` = required parameter. Paginated endpoints accept `page` (0-based), `size` (max 100) and "
           "`sort=field,asc|desc`.")
(DOCS / "API.md").write_text("\n".join(out) + "\n")
print(f"docs/API.md: {sum(len(v) for v in ops.values())} operations")
