(ns worthanot0.appstate)

(defonce app (atom {:username nil
                    :token nil
                    :login/fail nil
                    :register/fail nil}))
