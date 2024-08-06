import 'package:flutter/material.dart';
import 'package:openstore/search_page.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'OpenStore',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: Colors.blue,
          brightness: Brightness.dark,
        ),
        useMaterial3: true,
      ),
      home: const HomePage(),
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
            const SizedBox(width: 15,),
            Flexible(
              child: TextField(
                onSubmitted: (value) {
                  Navigator.of(context).push(
                    MaterialPageRoute(
                      builder: (context) {
                        return Scaffold(
                          appBar: AppBar(
                            title: Text("Поиск: "+value),
                            
                          ),
                          body: SearchPage(search: value,),
                        );
                      }
                    )
                  );
                },
                textInputAction: TextInputAction.search,
                decoration: InputDecoration(
                  prefixIcon: Icon(Icons.search),
                  border: OutlineInputBorder()
                ),
              ),
            ),
            //const SizedBox(width: 5,),
            //FilledButton(onPressed: (){}, child: Text("GO")),
            const SizedBox(width: 15,),
          ],
        ),
      ),
    );
  }

}