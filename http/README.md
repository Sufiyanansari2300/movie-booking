# HTTP requests

Prefer clicking around? Swagger UI is at http://localhost:8090/swagger-ui.html.

`catalog.http` walks through the API in order. Open it in IntelliJ and pick the `local` environment.

The admin password is read from `http-client.private.env.json` (git-ignored). Create it next to this file:

```json
{
  "local": {
    "adminPassword": "the value of app.admin.password in local.properties"
  }
}
```

If your admin email is not `admin@moviebooking.local`, add `"adminEmail": "..."` there as well.

Never put passwords in `http-client.env.json` - that file is committed. Use the private file above.
