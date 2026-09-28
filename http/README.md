# HTTP requests

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
