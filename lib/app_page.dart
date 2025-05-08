import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:flutter/material.dart';
import 'package:openstore/get_app_link.dart';
import 'package:url_launcher/url_launcher.dart';

class AppPage extends StatefulWidget {
  const AppPage({super.key, required this.packageName});
  final String packageName;
  @override
  State<AppPage> createState() => _AppPageState();
}

String getAndroidVersion(int apiLevel) {
  const versions = {
    1: '1.0',
    2: '1.1',
    3: '1.5',
    4: '1.6',
    5: '2.0',
    6: '2.0.1',
    7: '2.1',
    8: '2.2',
    9: '2.3',
    10: '2.3.3',
    11: '3.0',
    12: '3.1',
    13: '3.2',
    14: '4.0',
    15: '4.0.3',
    16: '4.1',
    17: '4.2',
    18: '4.3',
    19: '4.4',
    20: '4.4W', // Wear-версия
    21: '5.0',
    22: '5.1',
    23: '6.0',
    24: '7.0',
    25: '7.1',
    26: '8.0',
    27: '8.1',
    28: '9.0',
    29: '10',
    30: '11',
    31: '12',
    32: '12L', // Android 12L
    33: '13',
    34: '14',
  };

  return versions[apiLevel] ?? '? (API $apiLevel)';
}

class _AppPageState extends State<AppPage> {
  Map? _appInfo;
  @override
  void initState() {
    super.initState();
    _loadAppInfo();
  }

  void _loadAppInfo() async {
    var rq = await http.get(Uri.https(
      "backapi.rustore.ru",
      "/applicationData/overallInfo/${widget.packageName}",
    ));
    print(widget.packageName + "PN");
    if (rq.statusCode != 200) {
      showDialog(
          context: context,
          builder: (context) => AlertDialog(
                title: const Text("Что то пошло не так"),
                content: Text("RuStore вернул код ${rq.statusCode}"),
                actions: [
                  FilledButton(
                    onPressed: () {
                      Navigator.of(context)
                        ..pop()
                        ..pop();
                    },
                    child: const Text("ок"),
                  )
                ],
              ));
    }
    setState(() {
      _appInfo = json.decode(utf8.decode(rq.bodyBytes))["body"] ?? {};
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
        appBar: AppBar(),
        body: _appInfo != null
            ? ListView(
                padding: const EdgeInsets.fromLTRB(15, 0, 15, 15),
                children: [
                  Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      ClipRRect(
                        borderRadius: BorderRadius.circular(25),
                        child: Image.network(
                          _appInfo?["iconUrl"] ?? "",
                          width: 120,
                        ),
                      ),
                      const SizedBox(
                        width: 15,
                      ),
                      Expanded(
                          child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            _appInfo?["appName"] ?? "null",
                            //overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 21,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                          const SizedBox(
                            height: 10,
                          ),
                          Text(
                            _appInfo?["companyName"] ?? "null",
                            overflow: TextOverflow.ellipsis,
                            softWrap: false,
                          ),
                        ],
                      )),
                    ],
                  ),
                  const SizedBox(
                    height: 20,
                  ),
                  FilledButton.icon(
                    style: FilledButton.styleFrom(
                      minimumSize: const Size.fromHeight(40),
                    ),
                    onPressed: () async {
                      launchUrl(await getAppLink(_appInfo?["appId"], context));
                    },
                    label: const Text("Скачать APK"),
                  ),
                  const SizedBox(height: 15),
                  SizedBox(
                    height: 84,
                    child: ListView(
                      scrollDirection: Axis.horizontal,
                      children: [
                        _InfoCard(
                          topText:
                              "${((_appInfo?["fileSize"] ?? 0) / 1024 / 1024).toStringAsFixed(1)} MB",
                          bottomText: "Размер",
                        ),
                        _InfoCard(
                          topText: _appInfo?["versionName"] ?? "0",
                          bottomText: "Версия",
                        ),
                        _InfoCard(
                          topText:
                              "${getAndroidVersion(_appInfo?["minSdkVersion"] ?? 0)}+",
                          bottomText: "Android",
                        ),
                        _InfoCard(
                          topText:
                              _appInfo?["ageRestriction"]?["category"] ?? "?",
                          bottomText: "Возраст",
                        ),
                        _InfoCard(
                          topText: _appInfo?["categories"]?[0] ?? "?",
                          bottomText: "Категория",
                        )
                      ],
                    ),
                  ),
                  const SizedBox(height: 15),
                  SizedBox(
                    height: 250,
                    child: ListView.builder(
                        scrollDirection: Axis.horizontal,
                        itemCount: _appInfo?["fileUrls"]?.length ?? 0,
                        itemBuilder: (context, i) {
                          var file = _appInfo?["fileUrls"][i];
                          if (file["type"] != "SCREENSHOT" &&
                              file["orientation"] != "PORTRAIT") {
                            return const SizedBox();
                          }
                          //return Text("data${i}");
                          return Padding(
                            padding: const EdgeInsets.all(5),
                            child: ClipRRect(
                              borderRadius: BorderRadius.circular(15),
                              child: Image.network(
                                file["fileUrl"],
                              ),
                            ),
                          );
                        }),
                  ),
                  const SizedBox(height: 15),
                  const Text(
                    "Что нового?",
                    style: TextStyle(fontSize: 21, fontWeight: FontWeight.bold),
                  ),
                  Text(_appInfo?["whatsNew"] ?? "null"),
                  const SizedBox(height: 15),
                  const Text(
                    "Описание",
                    style: TextStyle(fontSize: 21, fontWeight: FontWeight.bold),
                  ),
                  Text(_appInfo?["fullDescription"] ?? "null"),
                ],
              )
            : const LinearProgressIndicator());
  }
}

class _InfoCard extends StatelessWidget {
  const _InfoCard({required this.topText, required this.bottomText});
  final String topText;
  final String bottomText;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(15),
        child: Column(
          children: [
            Text(
              topText,
              style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
            Text(bottomText),
          ],
        ),
      ),
    );
  }
}
