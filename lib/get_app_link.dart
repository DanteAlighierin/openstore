import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;

Future<Uri> getAppLink(int appId, BuildContext context) async {
  var rq = await http.post(
    Uri.https(
      "backapi.rustore.ru",
      "/applicationData/v2/download-link",
    ),
    body: json.encode({
      "appId": appId,
      "signatureFingerprint": null
    }),
    headers: {
      "Content-Type": "application/json"
    }
  );
  if (rq.statusCode != 200) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text("Что то пошло не так"),
        content: Text("RuStore вернул код ${rq.statusCode}"),
        actions: [
          FilledButton(
            onPressed: () {
              Navigator.of(context).pop();
            },
            child: const Text("ок"),
          )
        ],
      )
    );
  }
  var body = json.decode(utf8.decode(rq.bodyBytes))["body"];
  return Uri.parse(body["downloadUrls"][0]["url"]);
}