import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;
import 'package:openstore/app_page.dart';
import 'package:openstore/get_app_link.dart';

class SearchPage extends StatefulWidget {
  const SearchPage({super.key, required this.search});
  final String search;

  @override
  State<SearchPage> createState() => _SearchPageState();
}

class _SearchPageState extends State<SearchPage> {
  List? _searchResults;
  @override
  void initState() {
    super.initState();
    _loadResults();
  }
  void _loadResults() async {
    var rq = await http.get(
      Uri.https(
        "backapi.rustore.ru",
        "/applicationData/apps",
        {
          "pageSize": "20",
          "query": widget.search,
        }
      )
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
                Navigator.of(context)
                ..pop()
                ..pop();
              },
              child: const Text("ок"),
            )
          ],
        )
      );
    }
    setState(() {
      _searchResults = json.decode(utf8.decode(rq.bodyBytes))["body"]["content"];
    });
  }
  @override
  Widget build(BuildContext context) {
    if (_searchResults == null) {
      return const LinearProgressIndicator();
    }
    return ListView.builder(
      itemCount: _searchResults?.length,
      padding:  const EdgeInsets.fromLTRB(15, 0, 15, 0),
      itemBuilder: (context, i) {
        var appInfo = _searchResults?[i];
        return Card(
          clipBehavior: Clip.hardEdge,
          child: InkWell(
            onTap: () async {
              Navigator.of(context).push(
                MaterialPageRoute(builder: (ctx) =>
                  Scaffold(
                    appBar: AppBar(),
                    body: AppPage(packageName: appInfo["packageName"]),
                  )
                )
              );
            },
            child: Padding(
              padding: const EdgeInsets.all(15),
              child: Row(
                children: [
                  ClipRRect(
                    borderRadius: BorderRadius.circular(15),
                    child: Image.network(
                      width: 60,
                      appInfo["iconUrl"]
                    ),
                  ),
                  const SizedBox(width: 15,),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          appInfo["appName"],
                          style: const TextStyle(
                            fontWeight: FontWeight.bold,
                          ),
                          overflow: TextOverflow.ellipsis,
                        ),
                        Text(
                          appInfo["packageName"],
                          overflow: TextOverflow.ellipsis,
                        )
                      ],
                    ),
                  ),
                  IconButton.filledTonal(
                    onPressed: ()async{
                      Clipboard.setData(
                        ClipboardData(text: (await getAppLink(appInfo?["appId"], context)).toString())
                      );
                    },
                    icon: const Icon(Icons.link),
                  ),
                ],
              ),
            ),
          )
        );
      }
    );
  }
}