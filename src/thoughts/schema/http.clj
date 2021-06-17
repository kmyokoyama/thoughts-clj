(ns thoughts.schema.http
  (:require [schema.core :as s]))

(def SignupRequest
  {:name     s/Str
   :email    s/Str
   :username s/Str
   :password s/Str})

(def LoginRequest
  {:user-id  s/Str
   :password s/Str})

(def Thought {:text s/Str})

(def CreateThoughtRequest Thought)
(def ReplyRequest Thought)

(def RethoughtWithCommentRequest
  {:comment s/Str})