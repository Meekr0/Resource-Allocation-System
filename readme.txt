OPIS IMPLEMENTACJI: 
	
- Pierwszy węzeł w sieci (poniżej nazywany też "węzłem najwyższym") jest tworzony, zgodnie ze specyfikacją, poprzez niepodanie elementu -gateway <adres>:<port> przy tworzeniu. Wszystkie kolejne węzły wymagają podania tego elementu, by wykorzystać istniejący węzeł do połączenia się do sieci.
	
- Każdy węzeł posiada dwa elementy typu Map<String, Integer> - pierwsza z nich, resources - zawiera informacje o wszystkich zasobach którymi dysponuje ten węzeł. Przy stworzeniu węzła z zasobami <A:3>, <B:1> resources będzie zawierało wartości <A:3>, <B:1>. Druga mapa, resourcesInAndUnderMe, zawiera informacje o wszystkich zasobach jakie znajdują się w tym węźle oraz wszystkich węzłach znajdujących się 'pod nim' w hierarchii. Przez znajdowanie się pod węzłem, mam na myśli bycie podłączonym do sieci za jego pomocą, lub bycie podłączonym do sieci za pomocą węzła, który jest podłączony do sieci za jego pomocą itd. Mapa resourcesInAndUnderMe zawiera jedynie informacje o tym, jakie zasoby znajdują się pod węzłem X, nie jest jednak wykorzystywana do faktycznej alokacji zasobów. Dla węzłów 'najniższych' w hierarchii, tj. tych, które nie były wykorzystane do podłączenia się do sieci przez żaden inny węzeł, mapy resources oraz resourcesInAndUnderMe są sobie równe.
	
- Każdy węzeł zawiera informacje o tym, jakie węzły "użyły go" (bezpośrednio, bez użycia żadnych innych węzłów pośrednich) do podłączenia się do sieci, oraz o tym, który węzeł wykorzystały do podłączenia się do sieci (oprócz pierwszego).
	
- Węzeł X, łącząc się do sieci poprzez węzeł Y, rozpoczyna komunikacje poprzez wysłanie (przez TCP) do węzła Y następującej linijki:
	"NN FC <port> <Zasób:Liczba> <Zasób:Liczba> ... "
gdzie port to numer portu, na którym węzeł X będzie oczekiwać na kolejne połączenia. Flaga "NN", oznaczająca "NewNode", informuje węzeł Y o tym, że łączy się z nim inny węzeł w celu podłączenia się do sieci. Flaga "FC", oznaczająca "First Connection", informuje węzeł Y o tym, że węzeł X nie był wcześniej podłączony do sieci i wykorzystuje w tym celu węzeł Y. Wreszcie, <Zasób:Liczba>, to informacja od węzła X o tym, jakie zasoby w sobie zawiera. Węzeł Y dodaje te zasoby do mapy resourcesInAndUnderMe, oraz zapisuje węzeł X jako jeden z węzłów znajdujący się "pod nim" w hierarchii. Teraz, zakładając że węzeł Y wykorzystał węzeł Z w celu podłączenia się do sieci, węzeł Y wysyła do węzła Z linijkę:
	"NN NFC <port> <Zasób:Liczba> <Zasób:Liczba> ... "
gdzie port oraz <Zasób:Liczba> to są te same informacje, jakie wcześniej uzyskał od węzła X. Jedyną różnicą jest zmiana flagi z "FC" na "NFC" ("Not First Connection"). Otrzymując flagę "NFC" węzeł Z wie, że węzeł X został już podłączony do sieci (w tym wypadku, za pomocą węzła Y), i że nie powinien dopisywać go do swojej listy kontaktów. Niemniej jednak, dodaje zasoby z węzła X do swojej mapy resourcesInAndUnderMe (bo węzeł X znajduje się "pod nim" w hierarchii, nawet jeżeli niebezpośrednio). W ten sposób węzeł Z wie, ile zasobów może uzyskać szukając ich "w dół", a także może komunikować się z węzłem X za pośrednictwem węzła Y. Jeżeli natomiast węzeł Y NIE wykorzystał innego węzła w celu podłączenia się do sieci (tj. węzeł Y jest "najwyższym" w hierarchii), nie wysyła dalej żadnych linijek (bo nie ma dokąd). W tej sytuacji mapa resourcesInAndUnderMe w węźle Y 
	zawiera informacje o wszystkich zasobach dostępnych aktualnie w sieci.
		
	- Jeżeli przy nowym połączeniu, węzeł X nie otrzyma na początku linijki jednej z oczekiwanych flag, oznacza to że łączy się z nim klient. W tej sytuacji, węzeł X zapisuje informacje o kliencie (tj, identyfikator, adres oraz port klienta), oraz 
wczytuje, alokacji jakich zasobów zażądał klient. W pierwszej kolejności węzeł X sprawdza, czy sam w sobie posiada wystarczająco by je zaalokować. Jeżeli tak - alokuje podane zasoby, informuje o tym węzły powyżej (by te usunęły ich odpowiednią ilość ze swoich map resourcesInAndUnderMe) za pomocą wiadomości zaczynającej się od flagi "NAR" ("Node Allocated Resources"), oraz odsyła do klienta, że operacja się powiodła oraz przesyła mu jakie zasoby zaalokował (razem ze swoim adresem i portem, zgodnie ze specyfikacją). Jeżeli węzeł X nie posiada wystarczających zasobów, sprawdza, czy "pod nim" jest dostępna wystarczająca ilość (tj. w węźle X oraz w węzłach pod nim w hierarchii, czyli sprawdza mapę resourcesInAndUnderMe, a nie resources, jak robił to wcześniej). Jeżeli znajduje się tam wystarczająca ilość zasobów, węzeł X rozdziela żądanie na pojedyńcze zasoby i ich żądane ilości. 

Na potrzeby przykładu, załóżmy taką hierarchię: 
	> Węzeł X dysponuje zasobami <B:1>
	> Węzeł Y dysponuje zasobami <A:2>, <C:1>, oraz jest podłączony do sieci za pomocą węzła X
	> Węzeł Z dysponuje zasobami <A:1>, <C:1> oraz jest podłączony do sieci za pomocą węzła Y
Niech do węzła X połączy się klient, żądający alokacji: <A:3>, <B:1>, <C:2>. Sieć zawiera wystarczająco zasobów, ale nie sam węzeł X. Po rozdzieleniu zasobów, jako pierwszy chcemy zaalokować <A:3>. Patrzymy więc, czy <A:3> da się zaalokować w węźle X. Jeżeli nie, węzeł X pyta węzeł Y, czy tam da się zaalokować <A:3>. To zapytanie dzieje się za pomocą określonej flagi "NCR" ("Node Client Request"), i wiadomość z węzła X do węzła Y miała by postać: 
	"NCR A:3"
Analogicznie, jeżeli węzeł Y nie jest w stanie zaalokować <A:3>, pyta o to węzeł Z (znowu za pomocą flagi "NCR"). Gdyby sieć była dłuższa, węzeł Z zapytałby dalej. Skoro jednak doszliśmy do węzła Z, który nie może zaalokować tego zasobów, a także nie ma węzłów pod sobą, które może o to zapytać (a także widzi, że "pod nim" nie jest już dostępne <A:3>), odpowiada węzłowi Y, że nie był w stanie dokonać alokacji. Ta komunikacja odbywa się z wykorzystaniem UDP, za pomocą jednej z trzech flag: 
	"NRA" ("Node Request Allocated") - jeżeli alokacja się powiodła
	"NRF" ("Node Request Failed") - jeżeli się nie powiodła 
	"NRA_R" ("Node Request Allocated Recursively") - jeżeli alokacja się powiodła, ale wymagało to od węzła pośredniego zapytania "pod sobą"
W podanym przykładzie, węzeł Z przesyła za pomocą UDP do węzła Y wiadomość "NRF". Węzeł Y wysyła to samo do węzła X. Gdyby węzeł X miał więcej węzłów pod bezpośrednio pod sobą, powtórzyłby czynność dla każdego z nich. Skoro jednak nie udało się zaalokować nigdzie <A:3>, zmniejszami ilość zasobu jaką próbujemy zaalokować naraz. Węzeł X ponawia działanie, tym razem wysyłając wiadomość o postaci:
	"NCR A:2"
<A:2> możliwe było do zaalokowania w węźle Y, zatem węzeł Y alokuje podaną wartość i odsyła do węzła X wiadomość:
	"NRA"
Węzeł X redukuje wartość dla zasobu A w mapie resourcesInAndUnderMe o 2, a następnie patrzy ile jeszcze danego zasobu musi zaalokować. Dalej pozostaje <A:1>, zatem prosi węzeł Y o alokację, a węzeł Y prosi o to węzeł Z. Węzeł Z alokuje podany zasób, i odsyła do węzła Y wiadomość:
	"NRA"
Węzeł Y odejmuje odpowiednie zasoby z zasobów dostępnych pod nim, a następnie odsyła do węzła X wiadomość:
	"NRA_R gateway port"
gdzie gateway i port to adres oraz port węzła, który zaalokował zasoby (całość działa rekursywnie, dlatego nawet gdyby węzłów było więcej, węzeł X dalej otrzyma poprawne informacje o tym, który węzeł zaalokował zasoby). Węzeł X redukuje wartość zasobu A w mapie zasobów pod nim, a następnie patrzy, ile zostało do alokacji. W tym wypadku, zaalokowane zostało całe <A:3>, dlatego możemy przejść do następnego zasobu, czyli <B:1>. Całość procedury się powtarza, i gdy zaalokowany zostanie ostatni zasób, odsyłamy do klienta wiadomość 
	"ALLOCATED"
oraz informacje o tym, jakie zasoby, w jakiej ilości, i na jakim adresie zostały zaalokowane.
	
- Jeżeli w oraz pod węzłem X nie znajduje się wystarczająco zasobów żądanych przez klienta, pytamy "w górę" sieci. Jeżeli przyjmiemy przykładowy model sieci z poprzedniego punktu opisu, ale niech klient tym razem połączy się z węzłem Z, a nie X. Węzeł Z nie posiada wystarczająco zasobów, dlatego zapyta o to węzeł Y, wysyłając mu wiadomość (przez TCP):
	"NRU gateway cPort <Zasób:Liczba> <Zasób:Liczba> ... "
Gdzie gateway to adres węzła Z, a cPort to z góry ustalony, wybrany port (w tym wypadku jest on ustawiony na 5000), na którym węzeł Z będzie oczekiwać odpowiedzi (przez UDP). Flaga "NRU" oznacza "Node Request Up". Węzeł Y sprawdza, czy pod nim znajduje się wystarczająco zasobów, by obsłużyć żądanie, a jeżeli nie, przesyła tą samą wiadomość "w górę", tj. do swojego wyższego węzła. Jeżeli w ten sposób dojdziemy do węzła najwyższego, nie posiadającego żadnego węzła powyżej (a więc pierwszego w hierarchii, posiadającego informacje na temat całego systemu), i pod tym węzłem nie było wystarczająco zasobów do obsługi żądania, odesłałby on przez UDP do węzła Z wiadomość:
	"NRF"
W tej sytuacji oznacza to, że sieć nie ma wystarczających zasobów do obsługi żądania, a węzeł Z odeśle klientowi wiadomość:
	"FAILED"
Po czym zakończy połączenie, nie mogąc obsłużyć żadania.
W naszym przykładzie jednak wiadomość dociera do węzła X, pod którym znajduje się wystarczająco zasobów. Obsługuje on więc całość alokacji (w sposób identyczny jak ten opisany w poprzednim punkcie), a następnie przez UDP wysyła do węzła Z wiadomość:
	"NRA Zasób1:Liczba1:AdresGdzieZostałZaalokowany1:PortGdzieZostałZaalokowany1 Zasób2:Liczba2:AdresGdzieZostałZaalokowany2:PortGdzieZostałZaalokowany2 ... "
W tej sytuacji węzeł Z może odesłać klientowi wiadomość 
		"ALLOCATED"
oraz informacje o tym, jakie zasoby, w jakiej ilości, i na jakim adresie zostały zaalokowane.
	
- Gdy w wiadomości od klienta znajdzie się element "TERMINATE" (w sposób zgodny ze specyfikacją), węzeł powiadomi o tym wszystkie węzły podłączone bezpośrednio do niego oraz węzeł bezpośrednio nad nim za pomocą flagi "TERMINATE" (przesyłanej za pomocą TCP, jako jedynego elementu wiadomości), a następnie zakończy pracę. Wtedy powiadomione węzły powiadomią wszystkie swoje okoliczne węzły (z wyłączeniem węzła, który je o tym powiadomił, bo ten już wie), oraz zakończą swoją pracę. Całość będzie się powtarzać, dopóki cała sieć nie zakończy pracy.
	
CO ZOSTAŁO ZAIMPLEMENTOWANE:
	- Tworzenie sieci oraz dołączanie nowych węzłów do sieci przy wykorzystaniu dowolnego istniejącego węzła
	- Kończenie pracy sieci po otrzymaniu od klienta flagi "TERMINATE"
	- Komunikacja pomiędzy węzłami 
	- Komunikacja z klientem
	- Alokacja zasobów żądanych przez klienta (na węźle kontaktowym, jeżeli ten ma wystarczająco zasobów)
	- Alokacja zasobów żądanych przez klienta (na różnych węzłach rozłożonych w sposób dowolny w sieci, w zależności od tego jak są rozłożone)
	- Odmowa alokacji zasobów żądanych przez klienta, jeżeli w sieci nie ma wystarczająco
	
URUCHAMIANIE:
	- Załączone w archiwum pliki .class umieścić w jednym folderze ze skryptami, uruchomić skrypty
	- Uruchamianie jest w pełni zgodne ze specyfikacją i instrukcjami w niej zawartymi
	- W załączonym w archiwum folderze "dostarczoneSkrypty" znajdują się dostarczone na gakko skrypty do testowania - zostały one lekko przeze mnie zmodyfikowane, poprzez dodanie przed każdym poleceniem terminate komendy pause 
	(by można było zobaczyć, czy wszystko działa poprawnie). By je uruchomić, należy umieścić je w jednym folderze z wszystkimi trzema plikami .class
	- Wszystko da się uruchomić z wiersza poleceń, np
		java NetworkNode -ident 123 -tcpport 9990 A:1 B:1
		java NetworkNode -ident 124 -tcpport 9991 -gateway localhost:9990 A:5 C:3
		java NetworkClient -ident 321 -gateway localhost:9991 A:3 C:2 B:1
	po ustawieniu working directory na to w którym znajdują się odpowiednie pliki .class
	- Kompilacja za pomocą wiersza poleceń: 
		javac NetworkNode.java 
		javac NetworkClient.java
	po ustawieniu working directory na to w którym znajdują się odpowiednie pliki .java
	
UWAGI: 
- Nie stworzyłem własnej aplikacji klienta, wersja załączona z projektem to niezmieniona kopia aplikacji która została dostarczona w materiałach (nie licząc zmiany w komentarzach, bo z jakiegoś powodu się nie chciało kompilować, ale sam program jest bez zmian)
- Przy testowaniu z użyciem dostarczonych skryptów, bardzo rzadko zdarza się że nie zadziałają. Nie sądzę że to wina mojego programu, za każdym razem ponowne odpalenie skryptu rozwiązywało problem.
- Program może mieć problem jeżeli jeden z węzłów zostanie uruchomiony na porcie 5000 - ten port został przeze mnie zarezerwowany do komunikacji, i w niektórych sytuacjach (chociaż nie wszystkich, zależy od konkretnej sieci) może powodować problemy
