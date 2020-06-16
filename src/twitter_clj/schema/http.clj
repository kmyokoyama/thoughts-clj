(ns twitter-clj.schema.http
  (:require [schema.core :as s]))

(def SignupRequest
  {:name s/Str
   :email s/Str
   :username s/Str
   :password s/Str})

(def LoginRequest
  {:user-id s/Str
   :password s/Str})

(def Tweet {:text s/Str})

(def CreateTweetRequest Tweet)
(def ReplyRequest Tweet)

(def RetweetWithCommentRequest
  {:comment s/Str})