# SkyGenerator (Paper 1.21.4)

- /gen (alias: /generator, /generatory, /sg) - sklep z generatorami (GUI)
- Kupiony generator to blok - postaw go w świecie
- Prawy klik na generator (bez shifta) - magazyn: wyciągnij stack lub wszystko
- Złamanie własnego generatora zwraca blok + zawartość magazynu
- Stanie na generatorze ulepsza założoną zbroję (poziom = Ochrona)
- Admin: /sg give <gracz> <typ> [ilość], /sg reload

## Budowanie (JDK 21 + Maven)
    mvn clean package
Gotowy plik: target/SkyGenerator-1.0.0.jar -> folder plugins/

Wszystko (generatory, koszty, czasy, wiadomości) w config.yml.
Dane postawionych generatorów: plugins/SkyGenerator/data.yml

## Jar bez instalowania Javy (GitHub Actions)
1. Załóż darmowe konto na github.com i utwórz nowe repozytorium.
2. Wgraj do niego CAŁĄ zawartość tego folderu (przycisk "Add file" -> "Upload files"), razem z ukrytym folderem .github.
3. Wejdź w zakładkę Actions -> ostatni przebieg "Build" -> na dole Artifacts -> pobierz SkyGenerator-jar.
4. Rozpakuj pobrany zip - w środku jest SkyGenerator-1.0.0.jar. Wrzuć go do plugins/.
