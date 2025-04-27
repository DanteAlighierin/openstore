import 'package:flutter/material.dart';
import 'package:openstore/app_page.dart';
import 'package:openstore/search_page.dart';
import 'package:go_router/go_router.dart';

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
    return MaterialApp.router(
      title: 'OpenStore',
      theme: ThemeData(
        pageTransitionsTheme: const PageTransitionsTheme(builders: {
          TargetPlatform.android: PredictiveBackPageTransitionsBuilder(),
        }),
        colorScheme: ColorScheme.fromSeed(
          seedColor: Colors.blue,
          brightness: Brightness.dark,
        ),
        useMaterial3: true,
      ),
      routerConfig: _router,
    );
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
        child: Row(
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
                decoration: InputDecoration(
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
      ),
    );
  }
}
