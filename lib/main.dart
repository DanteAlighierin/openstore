import 'package:flutter/material.dart';
import 'package:openstore/app_page.dart';
import 'package:openstore/search_page.dart';
import 'package:go_router/go_router.dart';
import 'package:dynamic_color/dynamic_color.dart';
import 'package:url_launcher/url_launcher_string.dart';
import 'dart:io' show Platform;

void main() {
  runApp(const MyApp());
}

final _router = GoRouter(
  routes: [
    GoRoute(path: '/', builder: (context, state) => const HomePage(), routes: [
      GoRoute(
        path: '/app/:id',
        builder: (context, state) =>
            AppPage(packageName: state.pathParameters['id'] ?? ""),
      ),
      GoRoute(
          path: 'instruction',
          builder: (context, state) {
            print(state.uri.queryParameters["utm_campaign"]);
            return AppPage(packageName: "ru.nspk.mirpay");
          })
    ]),
    GoRoute(
        path: '/search',
        builder: (context, state) =>
            SearchPage(search: state.uri.queryParameters["q"] ?? "")),
    GoRoute(
        path: '/catalog/app/:id',
        redirect: (_, state) => "/app/${state.pathParameters["id"]}"),
  ],
);

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return DynamicColorBuilder(
        builder: (lightColorScheme, darkColorScheme) => MaterialApp.router(
              title: 'OpenStore',
              theme: ThemeData(
                pageTransitionsTheme: const PageTransitionsTheme(builders: {
                  TargetPlatform.android:
                      PredictiveBackPageTransitionsBuilder(),
                }),
                colorScheme: darkColorScheme ??
                    ColorScheme.fromSeed(
                      seedColor: Colors.blue,
                      brightness: Brightness.dark,
                    ),
                useMaterial3: true,
              ),
              routerConfig: _router,
            ));
  }
}

class HomePage extends StatelessWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
        appBar: AppBar(
          title: const Text("OpenStore"),
        ),
        body: Center(
          child: Column(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Container(),
                Row(
                  children: [
                    const SizedBox(
                      width: 15,
                    ),
                    Flexible(
                      child: TextField(
                        onSubmitted: (value) => context.push(
                            Uri(path: '/search', queryParameters: {'q': value})
                                .toString()),
                        textInputAction: TextInputAction.search,
                        decoration: const InputDecoration(
                            prefixIcon: Icon(Icons.search),
                            border: OutlineInputBorder()),
                      ),
                    ),
                    //const SizedBox(width: 5,),
                    //FilledButton(onPressed: (){}, child: Text("GO")),
                    const SizedBox(
                      width: 15,
                    ),
                  ],
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(0, 0, 0, 15),
                  child: Column(
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          InkWell(
                            onTap: () async => await launchUrlString(
                                "https://git.mi6e4ka.dev/mi6e4ka/openstore"),
                            child: const Text(
                              "source code",
                              style: TextStyle(
                                  decoration: TextDecoration.underline),
                            ),
                          ),
                          const SizedBox(
                            width: 8,
                          ),
                          !Platform.isAndroid ? const Text("|") : Container(),
                          !Platform.isAndroid
                              ? const SizedBox(
                                  width: 8,
                                )
                              : Container(),
                          !Platform.isAndroid
                              ? InkWell(
                                  onTap: () async => await launchUrlString(
                                      "https://git.mi6e4ka.dev/mi6e4ka/openstore/releases"),
                                  child: const Text(
                                    "android app",
                                    style: TextStyle(
                                        decoration: TextDecoration.underline),
                                  ),
                                )
                              : Container(),
                        ],
                      ),
                      const Text("v1.2.1")
                    ],
                  ),
                )
              ]),
        ));
  }
}
