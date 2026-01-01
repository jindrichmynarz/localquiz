(ns net.mynarz.localquiz.i18n
  (:require [taoensso.tempura :as tempura]))

(def dictionary
  {:cs {:and "a"
        :copy "Kopírovat"
        :copied "Zkopírováno!"
        :create-game "Vytvořit hru"
        :end-game "Ukončit hru"
        :errors {:player-name {:length "Jméno hráče musí mít mezi 1 až 20 znaky."
                               :taken "Hráč tohoto jména už je ve hře."}
                 :time-out "Čas vypršel!"}
        :footer {:made-with "Vyrobeno pomocí"
                 :persistence "nerozumnou vytrvalostí"
                 :using "s použitím"}
        :game-not-exists "Tato hra neexistuje!"
        :join-game "Hrát"
        :next "Další"
        :no-js "Váš prohlížeč nepodporuje JavaScript!"
        :player "Hráč"
        :player-name "Jméno hráče"
        :players "Hráči"
        :question {:yesno {:yes "Ano"
                           :no "Ne"}}
        :score "Skóre"
        :start-game "Zahájit hru"
        :submit "Odeslat"
        :switch-lang "Přepni jazyk"
        :upload-questions "Nahrát otázky"
        :wait-for-answers "Čekáme na další odpovědi..."
        :wait-for-game-start "Počkej prosím na zahájení hry."
        :wait-for-players "Čekáme alespoň na dva hráče..."
        :you-won "Vyhrál jsi!"}
   :en {:and "and"
        :copy "Copy"
        :copied "Copied!"
        :create-game "Create a game"
        :end-game "End game"
        :errors {:player-name {:length "Player name must have between 1 to 20 characters."
                               :taken "This name is already taken."}
                 :time-out "Time's out!"}
        :footer {:made-with "Made with"
                 :persistence "unreasonable persistence"
                 :using "using"}
        :game-not-exists "This game does not exist!"
        :join-game "Join game"
        :next "Next"
        :no-js "Your browser does not support JavaScript!"
        :player "Player"
        :player-name "Player name"
        :players "Players"
        :question {:yesno {:yes "Yes"
                           :no "No"}}
        :score "Score"
        :start-game "Start the game"
        :submit "Submit"
        :switch-lang "Switch the language"
        :uploat-questions "Upload questions"
        :wait-for-answers "Waiting for other answers..."
        :wait-for-game-start "Please wait for the game to start."
        :wait-for-players "Waiting for at least two players to join..."
        :you-won "You won!"}})

(def tr
  (partial tempura/tr {:dict dictionary}))
