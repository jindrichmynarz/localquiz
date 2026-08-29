(ns net.mynarz.localquiz.i18n)

(def dictionary
  {:cs {:accept "Souhlasím"
        :and "a"
        :answer "Odpověď"
        :close "Zavřít"
        :confirm-end-game "Opravdu chcete hru ukončit?"
        :consensus "Konsenzus"
        :consensus-evaluation "%1 % shoda"
        :cookie-warning "Localquiz pro své funkce používá cookies."
        :copy "Kopírovat"
        :copied "Zkopírováno!"
        :correct "Správně"
        :correctness "Správnost"
        :create-game "Vytvořit hru"
        :end-game "Ukončit hru"
        :errors {:errors "Chyby"
                 :max-upload-size-exceeded "Velikost nahraného souboru překračuje limit %1 MB."
                 :player-name {:length "Jméno hráče musí mít mezi 1 až 20 znaky."
                               :no-name "Hráč musí mít jméno."
                               :taken "Hráč tohoto jména už je ve hře."}
                 :game-not-joinable "Hra již začala."
                 :question-source-missing "Žádné otázky nebyly zadány!"
                 :time-out "Čas vypršel!"
                 :unknown-question-source "Neznámý zdroj otázek!"}
        :exit-game "Opustit hru"
        :footer {:made-by "Vyrobil"
                 :with "s"
                 :persistence "nerozumnou vytrvalostí"
                 :using "s použitím"}
        :frequency "Četnost"
        :game-not-exists "Tato hra neexistuje!"
        :incorrect "Nesprávně"
        :join-game "Hrát"
        :majority "Většina"
        :majority-failed "Získat většinu hlasů se nepodařilo!"
        :majority-gained "Získat většinu hlasů se podařilo!"
        :menu "Menu"
        :most-common-answers "Nejčetnější odpovědi"
        :next "Dále"
        :no-answer "Bez odpovědi"
        :no-js "Váš prohlížeč nepodporuje JavaScript!"
        :number-of-questions "Počet otázek"
        :pick-questions "Vyber otázky"
        :player "Hráč"
        :player-name "Jméno hráče"
        :players "Hráči"
        :players-answered "Zodpověděli"
        :point "bod"
        :point-fraction "bodu"
        :points "bodů"
        :question {:progress "Otázka %1/%2"
                   :yesno {:yes "Ano"
                           :no "Ne"}}
        :replay-audio "Přehrát znovu"
        :rules [[:h2 "Pravidla hry"]
                [:p "Hráči kvízu odpovídají na otázky a získávají body.
                     Hráč s nejvyšším počtem bodů po odehrání všech otázek se stává vítězem.
                     Nejvyšší skóre za odpověď je 1 bod.
                     Hráči mají na zodpovězení každé otázky %1 sekund.
                     Hráči, kteří nestihnou odpovědět, získávají 0 bodů."]
                [:h3 "Druhy skórování"]
                [:p "Skóre za odpověď je určeno druhem skórování otázky."]
                [:h4 "Správnost"]
                [:p "Pokud je otázka skórována dle **správnosti**, pak body získávají hráči, kteří odpoví správně.
                     Čím rychleji hráč odpoví správně, tím vyšší skóre získá.
                     Hráči, kteří odpoví nesprávně, nedostanou žádné body."]
                [:h4 "Konsenzus"]
                [:p "Pokud je otázka skórována dle **konsenzu**, pak body získávají hráči úměrně tomu, s kolika dalšími hráči odpoví stejně.
                     Například, pokud jsou všechny odpovědi shodné, všichni získávají 1 bod.
                     Pokud se hráč shodne s polovinou ostatních hráčů, získá půl bodu.
                     Rychlost odpovědi získané skóre neovlivňuje."]
                [:h4 "Většina"]
                [:p "Pokud je otázka skórována dle **většiny**, pak hráči získají bod, pokud odpoví stejně jako většina hráčů.
                     Když hráči nedosáhnou většinové shody, nikdo z nich nezíská žádné body.
                     Rychlost odpovědi získané skóre neovlivňuje."]
                [:h3 "Druhy otázek"]
                [:p "Kvízy mohou obsahovat různé druhy otázek."]
                [:h4 "Více možností"]
                [:p "Hráči dostanou možnost si vybrat z několika odpovědí."]
                [:h4 "Ano/ne"]
                [:p "Hráči odpovídají buď \"ano\" nebo \"ne\"."]
                [:h4 "Otevřená otázka"]
                [:p "Hráči můžou napsat jakoukoli odpověď.
                     Když se odpovědi porovnávají, například s očekávanou správnou odpovědí, malé překlepy jsou tolerovány."]
                [:h4 "Seřazování"]
                [:p "Hráči musí seřadit položky podle instrukcí v otázce.
                     Položky lze přeřadit jejich přetahováním."]
                [:h4 "Procentní odpověď"]
                [:p "Hráči odpovídají procentní hodnotou.
                     Rozdíly do 5 % jsou tolerovány, pokud je odpověď skórována dle správnosti."]
                [:h4 "Volba hráče"]
                [:p "Hráči musí vybrat jednoho z hráčů jako svou odpověď.
                    Je možné zvolit si sebe."]]
        :score "Skóre"
        :scoring "Hodnocení"
        :search "Vyhledávat..."
        :show-all-options "Zobrazit všechny možnosti"
        :start-game "Zahájit hru"
        :submit "Odeslat"
        :switch-lang "Přepni jazyk"
        :upload-questions "Nahrát otázky"
        :wait-for-answers "Čekáme na další odpovědi..."
        :wait-for-game-start "Počkej prosím na zahájení hry."
        :wait-for-players "Čekáme alespoň na dva hráče..."
        :you-lost "Prohrál jste, saláte."
        :you-won "Vítězství je tvé!"}
   :en {:accept "Accept"
        :and "and"
        :answer "Answer"
        :close "Close"
        :confirm-end-game "Do you want to end the game?"
        :consensus "Consensus"
        :consensus-evaluation "%1 % consensus"
        :cookie-warning "Localquiz uses cookies for its functionality."
        :copy "Copy"
        :copied "Copied!"
        :correct "Correct"
        :correctness "Correctness"
        :create-game "Create a game"
        :end-game "End game"
        :errors {:errors "Errors"
                 :max-upload-size-exceeded "Size of the uploaded file exceeds the limit of %1 MB."
                 :player-name {:length "Player name must have between 1 to 20 characters."
                               :no-name "The player must have a name."
                               :taken "This name is already taken."}
                 :game-not-joinable "The game has already started."
                 :question-source-missing "Question source missing!"
                 :time-out "Time's out!"
                 :unknown-question-source "Unknown question source!"}
        :exit-game "Exit game"
        :footer {:made-by "Made by"
                 :with "with"
                 :persistence "unreasonable persistence"
                 :using "using"}
        :frequency "Frequency"
        :game-not-exists "This game does not exist!"
        :incorrect "Incorrect"
        :join-game "Join game"
        :majority "Majority"
        :majority-failed "Gaining majority failed!"
        :majority-gained "Gaining majority succeeded!"
        :menu "Menu"
        :most-common-answers "Most common answers"
        :next "Next"
        :no-answer "No answer"
        :no-js "Your browser does not support JavaScript!"
        :number-of-questions "Number of questions"
        :pick-questions "Pick questions"
        :player "Player"
        :player-name "Player name"
        :players "Players"
        :players-answered "Answered"
        :point "point"
        :point-fraction "points"
        :points "points"
        :question {:progress "Question %1/%2"
                   :yesno {:yes "Yes"
                           :no "No"}}
        :replay-audio "Replay"
        :rules [[:h2 "Rules of the game"]
                [:p "This is a quiz: the players answer questions and score points.
                     The player with the highest score after all the questions are answered wins the game.
                     The maximum score for a question is 1 point.
                     The players have %1 seconds to answer each question.
                     Players who don't answer get no points."]
                [:h3 "Scoring methods"]
                [:p "Answer's scores are determined by the question's scoring method."]
                [:h4 "Correctness"]
                [:p "When the question's scoring method is **correctness**, the players who gave the correct answers score points.
                     The faster a player answers correctly, the higher their score is.
                     The players who answer incorrectly score no points."]
                [:h4 "Consensus"]
                [:p "When the question's scoring method is **consensus**, players score points in proportion to how many of the other players answer the same as them.
                     For example, if all players answer the same, everyone scores 1 point, achieving full consensus.
                     If a player's answer matches half of the other players, they score half a point.
                     Answer speed does not affect the score."]
                [:h4 "Majority"]
                [:p "When the question's scoring method is **majority**, the players score a point when they answer the same as the majority of the players.
                     If the players don't reach the majority consensus, none of them scores any points.
                     Answer speed does not affect the score."]
                [:h3 "Question types"]
                [:p "The quizzes can contain questions of different types."]
                [:h4 "Multiple choice"]
                [:p "The players are given multiple choices to pick their answers from."]
                [:h4 "Yes/no"]
                [:p "The players can answer either \"yes\" or \"no\"."]
                [:h4 "Open question"]
                [:p "The players can write any answer to the question.
                     When comparing the answers, for example with the expected correct answer, minor spelling differences are ignored."]
                [:h4 "Sorting question"]
                [:p "The players must sort a list according to the question's instructions.
                     Each list item can be dragged and dropped into its position."]
                [:h4 "Percentage range"]
                [:p "The players answer with a percentage.
                     Differences up to 5 % are ignored when scoring by correctness."]
                [:h4 "Player choice"]
                [:p "The players must pick one of the players as their answer.
                     Picking oneself is allowed."]]
        :score "Score"
        :scoring "Scoring"
        :search "Search..."
        :show-all-options "Show all options"
        :start-game "Start the game"
        :submit "Submit"
        :switch-lang "Switch the language"
        :upload-questions "Upload questions"
        :wait-for-answers "Waiting for other answers..."
        :wait-for-game-start "Please wait for the game to start."
        :wait-for-players "Waiting for at least two players to join..."
        :you-lost "You lost."
        :you-won "You won!"}})
