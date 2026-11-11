package com.oldpad.app.transfer

import android.content.Context
import android.os.StatFs
import com.oldpad.app.data.InternalLibrary
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * 平板上的局域网管理端。
 * 只在“电脑快传”页打开时启动，离开页面即停止；所有接口都需要 4 位 PIN。
 */
class TransferServer(
    private val appContext: Context,
    port: Int,
    private val pin: String
) : NanoHTTPD(port) {

    init {
        start(SOCKET_READ_TIMEOUT, false)
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        return try {
            when {
                session.method == Method.GET && (uri == "/" || uri == "/index.html") -> page()
                uri.startsWith("/api/") && !authorized(session) -> unauthorized()
                session.method == Method.GET && uri == "/api/state" -> state()
                session.method == Method.POST && uri == "/api/group" -> createGroup(session)
                session.method == Method.DELETE && uri == "/api/group" -> deleteGroup(session)
                session.method == Method.POST && uri == "/api/upload" -> upload(session)
                session.method == Method.DELETE && uri == "/api/file" -> deleteFile(session)
                else -> json(Response.Status.NOT_FOUND, error("接口不存在"))
            }
        } catch (e: Exception) {
            json(Response.Status.INTERNAL_ERROR, error(e.message ?: "服务器错误"))
        }
    }

    private fun authorized(session: IHTTPSession): Boolean {
        val queryPin = session.parameters["pin"]?.firstOrNull()
        val headerPin = session.headers["x-pin"]
        return queryPin == pin || headerPin == pin
    }

    private fun page(): Response {
        return try {
            val html = appContext.assets.open("transfer/index.html")
                .bufferedReader()
                .use { it.readText() }
            newFixedLengthResponse(
                Response.Status.OK,
                "text/html; charset=utf-8",
                html
            )
        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.OK,
                "text/plain; charset=utf-8",
                "快传页面未打包进 APK"
            )
        }
    }

    private fun state(): Response {
        val root = JSONObject().put("ok", true)
        root.put("free", freeBytes())
        root.put("videoGroups", groupsJson(InternalLibrary.TYPE_VIDEO))
        root.put("photoGroups", groupsJson(InternalLibrary.TYPE_PHOTO))
        return json(Response.Status.OK, root)
    }

    private fun groupsJson(type: String): JSONArray {
        val array = JSONArray()
        for (group in InternalLibrary.groups(appContext, type)) {
            val files = JSONArray()
            for (file in InternalLibrary.files(appContext, group)) {
                files.put(
                    JSONObject()
                        .put("name", file.name)
                        .put("size", file.length())
                )
            }
            array.put(
                JSONObject()
                    .put("id", group.id)
                    .put("name", group.name)
                    .put("type", group.type)
                    .put("files", files)
            )
        }
        return array
    }

    private fun createGroup(session: IHTTPSession): Response {
        val body = readBody(session)
        if (body.isBlank()) return json(Response.Status.BAD_REQUEST, error("内容为空"))
        val obj = JSONObject(body)
        val type = obj.optString("type")
        val name = obj.optString("name")
        val group = InternalLibrary.createGroup(appContext, type, name)
            ?: return json(Response.Status.CONFLICT, error("分组名重复或无效"))

        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("id", group.id)
                .put("name", group.name)
                .put("type", group.type)
        )
    }

    private fun deleteGroup(session: IHTTPSession): Response {
        val id = session.parameters["id"]?.firstOrNull()
            ?: return json(Response.Status.BAD_REQUEST, error("缺少分组 id"))
        val removed = InternalLibrary.deleteGroup(appContext, id)
        return if (removed) {
            json(Response.Status.OK, JSONObject().put("ok", true))
        } else {
            json(Response.Status.NOT_FOUND, error("分组不存在"))
        }
    }

    private fun upload(session: IHTTPSession): Response {
        val groupId = session.parameters["group"]?.firstOrNull()
            ?: return json(Response.Status.BAD_REQUEST, error("缺少分组"))
        val rawName = session.parameters["name"]?.firstOrNull()
            ?: return json(Response.Status.BAD_REQUEST, error("缺少文件名"))

        val group = InternalLibrary.group(appContext, groupId)
            ?: return json(Response.Status.NOT_FOUND, error("分组不存在"))

        val contentLength = session.headers["content-length"]?.toLongOrNull() ?: 0L
        if (contentLength <= 0) {
            return json(Response.Status.BAD_REQUEST, error("空文件"))
        }
        val free = freeBytes()
        if (contentLength > free - 100L * 1024 * 1024) {
            return json(Response.Status.SERVICE_UNAVAILABLE, error("平板空间不足"))
        }

        val files = HashMap<String, String>()
        session.parseBody(files)
        val tempPath = files["file"]
            ?: return json(Response.Status.BAD_REQUEST, error("没有收到文件"))
        val temp = File(tempPath)
        if (!temp.exists() || temp.length() <= 0) {
            temp.delete()
            return json(Response.Status.BAD_REQUEST, error("文件为空"))
        }

        val target = InternalLibrary.uniqueTarget(
            InternalLibrary.groupDir(appContext, group),
            rawName
        )
        temp.copyTo(target, overwrite = false)
        temp.delete()

        return json(
            Response.Status.OK,
            JSONObject()
                .put("ok", true)
                .put("name", target.name)
                .put("size", target.length())
        )
    }

    private fun deleteFile(session: IHTTPSession): Response {
        val groupId = session.parameters["group"]?.firstOrNull()
            ?: return json(Response.Status.BAD_REQUEST, error("缺少分组"))
        val rawName = session.parameters["name"]?.firstOrNull()
            ?: return json(Response.Status.BAD_REQUEST, error("缺少文件名"))
        val group = InternalLibrary.group(appContext, groupId)
            ?: return json(Response.Status.NOT_FOUND, error("分组不存在"))

        val dir = InternalLibrary.groupDir(appContext, group)
        val file = File(dir, InternalLibrary.safeFileName(rawName))
        if (!file.canonicalPath.startsWith(dir.canonicalPath + File.separator)) {
            return json(Response.Status.BAD_REQUEST, error("非法文件名"))
        }
        val deleted = file.exists() && file.delete()
        return if (deleted) {
            json(Response.Status.OK, JSONObject().put("ok", true))
        } else {
            json(Response.Status.NOT_FOUND, error("文件不存在"))
        }
    }

    private fun readBody(session: IHTTPSession): String {
        val length = session.headers["content-length"]?.toLongOrNull() ?: 0L
        if (length <= 0 || length > 1_000_000) return ""
        val buffer = ByteArray(length.toInt())
        var offset = 0
        try {
            while (offset < buffer.size) {
                val read = session.inputStream.read(buffer, offset, buffer.size - offset)
                if (read <= 0) break
                offset += read
            }
        } catch (e: IOException) {
            return ""
        }
        return String(buffer, 0, offset, Charsets.UTF_8)
    }

    private fun freeBytes(): Long {
        return try {
            val stat = StatFs(InternalLibrary.root(appContext).absolutePath)
            stat.availableBytes
        } catch (e: Exception) {
            0L
        }
    }

    private fun error(message: String): JSONObject =
        JSONObject().put("ok", false).put("error", message)

    private fun json(status: Response.Status, obj: JSONObject): Response =
        newFixedLengthResponse(
            status,
            "application/json; charset=utf-8",
            obj.toString()
        )

    private fun unauthorized(): Response =
        json(Response.Status.UNAUTHORIZED, error("PIN 不正确"))
}
